"""
TravelPlanner benchmark evaluator.

Ports the evaluation logic from TravelPlanner (commonsense_constraint + hard_constraint)
to work with local data files without HuggingFace dependency at evaluation time.
"""

from __future__ import annotations

import json
import math
import re
from pathlib import Path

import numpy as np
import pandas as pd

# ---------------------------------------------------------------------------
# Path setup
# ---------------------------------------------------------------------------
_BENCHMARK_DIR = Path(__file__).resolve().parent
_PROJECT_ROOT = _BENCHMARK_DIR.parent.parent
_DB_DIR = _PROJECT_ROOT / "TravelPlanner" / "database" / "database"
_REF_DIR = _PROJECT_ROOT / "TravelPlanner" / "database"


# ---------------------------------------------------------------------------
# Data loading helpers
# ---------------------------------------------------------------------------

def load_line_json_data(filename: str | Path) -> list[dict]:
    data = []
    with open(filename, "r", encoding="utf-8") as f:
        for line in f.read().strip().split("\n"):
            if line.strip():
                data.append(json.loads(line))
    return data


def load_queries(set_type: str = "train") -> list[dict]:
    """
    Load query data. First tries local JSONL cache, then falls back to HuggingFace.
    Uses HF_ENDPOINT env var for mirror support (e.g. https://hf-mirror.com).
    """
    cache_path = _REF_DIR / f"{set_type}_queries.jsonl"
    if cache_path.exists():
        print(f"Loading cached queries from {cache_path}")
        return load_line_json_data(cache_path)

    print(f"Downloading {set_type} split from HuggingFace...")
    from datasets import load_dataset
    ds = load_dataset("osunlp/TravelPlanner", set_type)
    split_key = list(ds.keys())[0]
    rows = [dict(row) for row in ds[split_key]]

    # Cache locally (without large fields)
    with open(cache_path, "w", encoding="utf-8") as f:
        for row in rows:
            row.pop("annotated_plan", None)
            row.pop("reference_information", None)
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"Cached {len(rows)} queries to {cache_path}")
    return rows


def load_ref_info(set_type: str = "train") -> list[dict]:
    path = _REF_DIR / f"{set_type}_ref_info.jsonl"
    return load_line_json_data(path)


# ---------------------------------------------------------------------------
# Sandbox databases
# ---------------------------------------------------------------------------

class SandboxDB:
    """Loads all CSV databases needed for evaluation."""

    def __init__(self):
        self.flights = pd.read_csv(
            _DB_DIR / "flights" / "clean_Flights_2022.csv"
        ).dropna()[["Flight Number", "Price", "DepTime", "ArrTime",
                     "ActualElapsedTime", "FlightDate", "OriginCityName",
                     "DestCityName", "Distance"]]

        self.accommodations = pd.read_csv(
            _DB_DIR / "accommodations" / "clean_accommodations_2022.csv"
        ).dropna()[["NAME", "price", "room type", "house_rules",
                     "minimum nights", "maximum occupancy",
                     "review rate number", "city"]]

        self.restaurants = pd.read_csv(
            _DB_DIR / "restaurants" / "clean_restaurant_2022.csv"
        ).dropna()[["Name", "Average Cost", "Cuisines",
                     "Aggregate Rating", "City"]]

        self.attractions = pd.read_csv(
            _DB_DIR / "attractions" / "attractions.csv"
        ).dropna()[["Name", "Latitude", "Longitude", "Address",
                     "Phone", "Website", "City"]]

        self.distance = pd.read_csv(
            _DB_DIR / "googleDistanceMatrix" / "distance.csv"
        )

        city_state_file = _DB_DIR / "background" / "citySet_with_states.txt"
        city_state_set = open(city_state_file, "r").read().split("\n")
        self.city_state_map = {
            parts[0]: parts[1]
            for unit in city_state_set
            if "\t" in unit
            for parts in [unit.split("\t")]
        }

        print("Sandbox DB loaded.")


# Singleton
_db: SandboxDB | None = None


def get_db() -> SandboxDB:
    global _db
    if _db is None:
        _db = SandboxDB()
    return _db


# ---------------------------------------------------------------------------
# Helper functions (ported from TravelPlanner/utils/func.py)
# ---------------------------------------------------------------------------

def extract_before_parenthesis(s: str) -> str:
    match = re.search(r"^(.*?)\([^)]*\)", s)
    return match.group(1) if match else s


def get_valid_name_city(info: str) -> tuple[str, str]:
    pattern = r"(.*?),\s*([^,]+)(\(\w[\w\s]*\))?$"
    match = re.search(pattern, info)
    if match:
        return match.group(1).strip(), extract_before_parenthesis(match.group(2).strip()).strip()
    return "-", "-"


def extract_from_to(text: str) -> tuple[str | None, str | None]:
    pattern = r"from\s+(.+?)\s+to\s+([^,]+)(?=[,\s]|$)"
    matches = re.search(pattern, text)
    return matches.groups() if matches else (None, None)


def count_consecutive_values(lst: list) -> list:
    if not lst:
        return []
    result = []
    current_string = lst[0]
    count = 1
    for i in range(1, len(lst)):
        if lst[i] == current_string:
            count += 1
        else:
            result.append((current_string, count))
            current_string = lst[i]
            count = 1
    result.append((current_string, count))
    return result


def transportation_match(text: str) -> str | None:
    if "taxi" in text.lower():
        return "Taxi"
    elif "self-driving" in text.lower():
        return "Self-driving"
    elif "flight" in text.lower():
        return "Flight"
    return None


def is_valid_city_sequence(city_list: list) -> bool:
    if len(city_list) < 3:
        return False
    visited_cities = set()
    i = 0
    while i < len(city_list):
        city = city_list[i]
        if city in visited_cities and (i != 0 and i != len(city_list) - 1):
            return False
        count = 0
        while i < len(city_list) and city_list[i] == city:
            count += 1
            i += 1
        if count == 1 and 0 < i - 1 < len(city_list) - 1:
            return False
        visited_cities.add(city)
    return True


# ---------------------------------------------------------------------------
# Distance matrix helper
# ---------------------------------------------------------------------------

def get_distance_cost(origin: str, destination: str, mode: str) -> dict:
    db = get_db()
    origin = extract_before_parenthesis(origin)
    destination = extract_before_parenthesis(destination)
    info = {"origin": origin, "destination": destination, "cost": None, "duration": None, "distance": None}
    response = db.distance[(db.distance["origin"] == origin) & (db.distance["destination"] == destination)]
    if len(response) > 0:
        dur = response["duration"].values[0]
        dist = response["distance"].values[0]
        if dur is None or dist is None or (isinstance(dur, float) and np.isnan(dur)) or (isinstance(dist, float) and np.isnan(dist)):
            return info
        info["duration"] = dur
        info["distance"] = dist
        if "day" not in str(dur):
            if "driving" in mode:
                info["cost"] = int(eval(str(dist).replace("km", "").replace(",", "")) * 0.05)
            elif mode == "taxi":
                info["cost"] = int(eval(str(dist).replace("km", "").replace(",", "")))
    return info


# ---------------------------------------------------------------------------
# Commonsense constraints (8 checks)
# ---------------------------------------------------------------------------

def is_valid_information_in_current_city(question: dict, tested_data: list) -> tuple[bool, str | None]:
    db = get_db()
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        current_city = unit.get("current_city", "")
        final_city_list = _get_city_list_from_value(current_city)

        if unit.get("transportation") and unit["transportation"] != "-":
            for city in final_city_list:
                if city not in unit["transportation"]:
                    return False, f"The transportation in day {i+1} is invalid city choice."

        for meal_key in ["breakfast", "lunch", "dinner"]:
            val = unit.get(meal_key)
            if val and val != "-":
                flag = any(city in val for city in final_city_list)
                if not flag:
                    return False, f"The {meal_key} in day {i+1} is invalid city choice."

        if unit.get("attraction") and unit["attraction"] != "-":
            attraction_list = unit["attraction"].split(";")[:-1] if unit["attraction"].endswith(";") else unit["attraction"].split(";")
            for attraction in attraction_list:
                if attraction.strip():
                    flag = any(city in attraction for city in final_city_list)
                    if not flag:
                        return False, f"The attraction in day {i+1} is invalid city choice."

        if unit.get("accommodation") and unit["accommodation"] != "-":
            if final_city_list[-1] not in unit["accommodation"]:
                return False, f"The accommodation in day {i+1} is invalid city choice."

    return True, None


def is_valid_information_in_sandbox(question: dict, tested_data: list) -> tuple[bool, str | None]:
    db = get_db()
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]

        # Transportation
        if unit.get("transportation") and unit["transportation"] != "-":
            value = unit["transportation"]
            org_city, dest_city = extract_from_to(value)
            if org_city is None or dest_city is None:
                org_city, dest_city = extract_from_to(unit.get("current_city", ""))
            if "flight number" in value.lower():
                try:
                    org_city = extract_before_parenthesis(org_city)
                    dest_city = extract_before_parenthesis(dest_city)
                except TypeError:
                    return False, f"The transportation in day {i+1} cannot be parsed."
                flight_num = value.split("Flight Number: ")[1].split(",")[0]
                if len(db.flights[
                    (db.flights["Flight Number"] == flight_num) &
                    (db.flights["OriginCityName"] == org_city) &
                    (db.flights["DestCityName"] == dest_city)
                ]) < 1:
                    return False, f"The flight number in day {i+1} is invalid in the sandbox."
            elif "self-driving" in value.lower() or "taxi" in value.lower():
                try:
                    org_city = extract_before_parenthesis(org_city)
                    dest_city = extract_before_parenthesis(dest_city)
                except TypeError:
                    org_city = "-"
                    dest_city = "-"
                if "self-driving" in value.lower():
                    if get_distance_cost(org_city, dest_city, "self-driving")["cost"] is None:
                        return False, f"The self-driving in day {i+1} is invalid in the sandbox."
                else:
                    if get_distance_cost(org_city, dest_city, "taxi")["cost"] is None:
                        return False, f"The taxi in day {i+1} is invalid in the sandbox."

        # Meals
        for meal_key in ["breakfast", "lunch", "dinner"]:
            val = unit.get(meal_key)
            if val and val != "-":
                name, city = get_valid_name_city(val)
                if len(db.restaurants[
                    (db.restaurants["Name"].astype(str).str.contains(re.escape(name))) &
                    (db.restaurants["City"] == city)
                ]) < 1:
                    return False, f"The {meal_key} in day {i+1} is invalid in the sandbox."

        # Attractions
        if unit.get("attraction") and unit["attraction"] != "-":
            attraction_list = unit["attraction"].split(";")[:-1] if unit["attraction"].endswith(";") else unit["attraction"].split(";")
            for attraction in attraction_list:
                if attraction.strip():
                    name, city = get_valid_name_city(attraction)
                    if len(db.attractions[
                        (db.attractions["Name"].astype(str).str.contains(re.escape(name))) &
                        (db.attractions["City"] == city)
                    ]) < 1:
                        return False, f"The attraction {attraction} in day {i+1} is invalid in the sandbox."

        # Accommodation
        if unit.get("accommodation") and unit["accommodation"] != "-":
            name, city = get_valid_name_city(unit["accommodation"])
            if len(db.accommodations[
                (db.accommodations["NAME"].astype(str).str.contains(re.escape(name))) &
                (db.accommodations["city"] == city)
            ]) < 1:
                return False, f"The accommodation in day {i+1} is invalid in the sandbox."

    return True, None


def _is_travel_day(city_value: str) -> bool:
    """Check if a current_city value indicates a travel day."""
    return "from " in city_value or (" to " in city_value and "from" not in city_value)


def _parse_travel_cities(city_value: str) -> tuple[str, str]:
    """Parse origin and destination from travel day city value."""
    if "from" in city_value:
        return extract_from_to(city_value)
    # Handle "X to Y" without "from"
    pattern = r"(.+?)\s+to\s+(.+?)(?:\s*$)"
    match = re.search(pattern, city_value)
    return match.groups() if match else (None, None)


def _get_city_list_from_value(city_value: str) -> list[str]:
    """Extract city list from a current_city value."""
    if _is_travel_day(city_value):
        city1, city2 = _parse_travel_cities(city_value)
        if city1 and city2:
            return [extract_before_parenthesis(city1), extract_before_parenthesis(city2)]
    return [extract_before_parenthesis(city_value)]


def is_reasonable_visiting_city(question: dict, tested_data: list) -> tuple[bool, str | None]:
    db = get_db()
    city_list = []
    for i in range(min(question["days"], len(tested_data))):
        city_value = tested_data[i].get("current_city", "")
        parsed = _get_city_list_from_value(city_value)
        if i == 0 and len(parsed) > 1 and parsed[0] != question["org"]:
            return False, f"The first day's city should be {question['org']}."
        city_list += parsed

    if len(city_list) < 3:
        return False, "City list too short."
    if city_list[0] != city_list[-1]:
        return False, "The trip should be a closed circle."
    if not is_valid_city_sequence(city_list):
        return False, "The city sequence is invalid."
    for idx, city in enumerate(city_list):
        if city not in db.city_state_map:
            return False, f"{city} is not a valid city."
        if idx not in [0, len(city_list) - 1] and question["days"] > 3 and db.city_state_map[city] != question["dest"]:
            return False, f"{city} is not in {question['dest']}."

    return True, None


def is_valid_restaurants(question: dict, tested_data: list) -> tuple[bool, str | None]:
    restaurants_list = []
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        for meal_key in ["breakfast", "lunch", "dinner"]:
            val = unit.get(meal_key)
            if val and val != "-":
                if val not in restaurants_list:
                    restaurants_list.append(val)
                else:
                    return False, f"The restaurant in day {i+1} {meal_key} is repeated."
    return True, None


def is_valid_attractions(question: dict, tested_data: list) -> tuple[bool, str | None]:
    attractions_list = []
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        if unit.get("attraction") and unit["attraction"] != "-":
            attraction_items = unit["attraction"].split(";")[:-1] if unit["attraction"].endswith(";") else unit["attraction"].split(";")
            for attraction in attraction_items:
                if attraction.strip():
                    if attraction not in attractions_list:
                        attractions_list.append(attraction)
                    else:
                        return False, f"The attraction '{attraction}' in day {i+1} is repeated."
    return True, None


def is_valid_transportation(question: dict, tested_data: list) -> tuple[bool, str | None]:
    if not tested_data:
        return False, "No data."
    first = tested_data[0].get("transportation")
    if not first or first == "-":
        return False, "The transportation in day 1 should not be empty."

    transportation_list = [transportation_match(first)]
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        if unit.get("transportation") and unit["transportation"] != "-":
            transportation_list.append(transportation_match(unit["transportation"]))

    if (("Self-driving" in transportation_list) and ("Flight" in transportation_list)) or \
       (("Taxi" in transportation_list) and ("Self-driving" in transportation_list)):
        return False, "The transportation is conflicting."

    return True, None


def is_valid_accommodation(question: dict, tested_data: list) -> tuple[bool, str | None]:
    db = get_db()
    data = []
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        if "accommodation" not in unit:
            return False, "No Accommodation Info."
        data.append(unit.get("accommodation", "-"))

    consecutive = count_consecutive_values(data)
    for unit in consecutive:
        if unit and unit[0] not in ["-", ""]:
            name, city = get_valid_name_city(unit[0])
            res = db.accommodations[
                (db.accommodations["NAME"].astype(str).str.contains(re.escape(name))) &
                (db.accommodations["city"] == city)
            ]
            if len(res) == 1 and unit[1] < res.iloc[0]["minimum nights"]:
                return False, f"The accommodation {unit[0]} does not obey the minimum nights rule."

    return True, None


def is_valid_visiting_city_number(question: dict, tested_data: list) -> tuple[bool, str | None]:
    city_set = set()
    for i in range(min(question["days"], len(tested_data))):
        city_value = tested_data[i].get("current_city", "")
        parsed = _get_city_list_from_value(city_value)
        if i == 0 and len(parsed) > 1 and parsed[0] != question["org"]:
            return False, f"The first day's city should be {question['org']}."
        for c in parsed:
            city_set.add(c)

    city_set.discard(question["org"])
    if len(city_set) != question["visiting_city_number"]:
        return False, f"The number of visiting cities should be {question['visiting_city_number']}."
    return True, None


def is_valid_days(question: dict, tested_data: list) -> tuple[bool, str | None]:
    lens = 0
    for i in range(min(question["days"], len(tested_data))):
        if tested_data[i] and tested_data[i].get("current_city") != "You don't need to fill in the information for this or later days.":
            lens += 1
    if lens != question["days"]:
        return False, f"The number of days should be {question['days']}."
    return True, None


def is_not_absent(question: dict, tested_data: list) -> tuple[bool, str | None]:
    needed_info = 6 * question["days"]
    total_valid_info = 0

    if not is_valid_days(question, tested_data)[0]:
        return False, "Invalid Days"
    if not is_valid_visiting_city_number(question, tested_data)[0]:
        return False, "Invalid City Number"

    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        for key in ["transportation", "breakfast", "lunch", "dinner", "attraction", "accommodation"]:
            if key not in unit:
                return False, f"No {key.title()} Info."

        current_city = unit.get("current_city", "")
        has_travel = _is_travel_day(current_city)

        if has_travel and unit.get("transportation") in ["", "-"]:
            return False, f"No transportation in day {i+1} is not allowed."
        if not has_travel and unit.get("attraction") in ["", "-"]:
            return False, f"No attraction in day {i+1} is not allowed."
        if i != question["days"] - 1 and unit.get("accommodation") in ["", "-"]:
            return False, f"No accommodation in day {i+1} is not allowed."
        if not has_travel and (unit.get("breakfast") in ["", "-"] or unit.get("lunch") in ["", "-"] or unit.get("dinner") in ["", "-"]):
            return False, f"No meal in day {i+1} is not allowed."

        for key in unit:
            if unit[key] and unit[key] != "-":
                total_valid_info += 1

    if total_valid_info * 1.0 / needed_info < 0.5:
        return False, "The absent information is more than 50%."

    return True, None


def _ensure_constraints_parsed(question: dict) -> dict:
    """Parse local_constraint if it's a string."""
    if isinstance(question.get("local_constraint"), str):
        question["local_constraint"] = eval(question["local_constraint"])
    return question


def commonsense_evaluation(question: dict, tested_data: list) -> dict:
    question = _ensure_constraints_parsed(question)
    return {
        "is_reasonable_visiting_city": is_reasonable_visiting_city(question, tested_data),
        "is_valid_restaurants": is_valid_restaurants(question, tested_data),
        "is_valid_attractions": is_valid_attractions(question, tested_data),
        "is_valid_accommodation": is_valid_accommodation(question, tested_data),
        "is_valid_transportation": is_valid_transportation(question, tested_data),
        "is_valid_information_in_current_city": is_valid_information_in_current_city(question, tested_data),
        "is_valid_information_in_sandbox": is_valid_information_in_sandbox(question, tested_data),
        "is_not_absent": is_not_absent(question, tested_data),
    }


# ---------------------------------------------------------------------------
# Hard constraints (5 checks)
# ---------------------------------------------------------------------------

def get_total_cost(question: dict, tested_data: list) -> float:
    db = get_db()
    total_cost = 0.0
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]

        # Transportation
        if unit.get("transportation") and unit["transportation"] != "-":
            value = unit["transportation"]
            org_city, dest_city = extract_from_to(value)
            if org_city is None or dest_city is None:
                org_city, dest_city = extract_from_to(unit.get("current_city", ""))
            if org_city and dest_city:
                if "flight number" in value.lower():
                    flight_num = value.split("Flight Number: ")[1].split(",")[0]
                    res = db.flights[db.flights["Flight Number"] == flight_num]
                    if len(res) > 0:
                        total_cost += res["Price"].values[0] * question["people_number"]
                elif "self-driving" in value.lower() or "taxi" in value.lower():
                    if "self-driving" in value.lower():
                        cost = get_distance_cost(org_city, dest_city, "self-driving")["cost"]
                        if cost is not None:
                            total_cost += cost * math.ceil(question["people_number"] * 1.0 / 5)
                    else:
                        cost = get_distance_cost(org_city, dest_city, "taxi")["cost"]
                        if cost is not None:
                            total_cost += cost * math.ceil(question["people_number"] * 1.0 / 4)

        # Meals
        for meal_key in ["breakfast", "lunch", "dinner"]:
            val = unit.get(meal_key)
            if val and val != "-":
                name, city = get_valid_name_city(val)
                res = db.restaurants[
                    (db.restaurants["Name"].astype(str).str.contains(re.escape(name))) &
                    (db.restaurants["City"] == city)
                ]
                if len(res) > 0:
                    total_cost += res["Average Cost"].values[0] * question["people_number"]

        # Accommodation
        if unit.get("accommodation") and unit["accommodation"] != "-":
            name, city = get_valid_name_city(unit["accommodation"])
            res = db.accommodations[
                (db.accommodations["NAME"].astype(str).str.contains(re.escape(name))) &
                (db.accommodations["city"] == city)
            ]
            if len(res) > 0:
                total_cost += res["price"].values[0] * math.ceil(
                    question["people_number"] * 1.0 / res["maximum occupancy"].values[0]
                )

    return total_cost


def is_valid_room_rule(question: dict, tested_data: list) -> tuple[bool | None, str | None]:
    db = get_db()
    if question.get("local_constraint", {}).get("house rule") is None:
        return None, None
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        if unit.get("accommodation") and unit["accommodation"] != "-":
            name, city = get_valid_name_city(unit["accommodation"])
            res = db.accommodations[
                (db.accommodations["NAME"].astype(str).str.contains(re.escape(name))) &
                (db.accommodations["city"] == city)
            ]
            if len(res) > 0:
                rules = str(res["house_rules"].values[0])
                hr = question["local_constraint"]["house rule"]
                if hr == "smoking" and "No smoking" in rules:
                    return False, f"The house rule should be {hr}."
                if hr == "parties" and "No parties" in rules:
                    return False, f"The house rule should be {hr}."
                if hr == "children under 10" and "No children under 10" in rules:
                    return False, f"The house rule should be {hr}."
                if hr == "visitors" and "No visitors" in rules:
                    return False, f"The house rule should be {hr}."
                if hr == "pets" and "No pets" in rules:
                    return False, f"The house rule should be {hr}."
    return True, None


def is_valid_cuisine(question: dict, tested_data: list) -> tuple[bool | None, str | None]:
    db = get_db()
    cuisine_req = question.get("local_constraint", {}).get("cuisine")
    if not cuisine_req:
        return None, None
    cuisine_set = set()
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        for meal_key in ["breakfast", "lunch", "dinner"]:
            val = unit.get(meal_key)
            if val and val != "-":
                name, city = get_valid_name_city(val)
                if city == question["org"]:
                    continue
                res = db.restaurants[
                    (db.restaurants["Name"].astype(str).str.contains(re.escape(name))) &
                    (db.restaurants["City"] == city)
                ]
                if len(res) > 0:
                    for cuisine in cuisine_req:
                        if cuisine in res.iloc[0]["Cuisines"]:
                            cuisine_set.add(cuisine)
    if len(cuisine_set) == len(cuisine_req):
        return True, None
    for cuisine in cuisine_req:
        if cuisine not in cuisine_set:
            return False, f"The cuisine {cuisine} is not satisfied."
    return True, None


def is_valid_room_type(question: dict, tested_data: list) -> tuple[bool | None, str | None]:
    db = get_db()
    rt = question.get("local_constraint", {}).get("room type")
    if rt is None:
        return None, None
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        if unit.get("accommodation") and unit["accommodation"] != "-":
            name, city = get_valid_name_city(unit["accommodation"])
            res = db.accommodations[
                (db.accommodations["NAME"].astype(str).str.contains(re.escape(name))) &
                (db.accommodations["city"] == city)
            ]
            if len(res) > 0:
                actual = res["room type"].values[0]
                if rt == "not shared room" and actual == "Shared room":
                    return False, f"The room type should be {rt}."
                if rt == "shared room" and actual != "Shared room":
                    return False, f"The room type should be {rt}."
                if rt == "private room" and actual != "Private room":
                    return False, f"The room type should be {rt}."
                if rt == "entire room" and actual != "Entire home/apt":
                    return False, f"The room type should be {rt}."
    return True, None


def is_valid_transportation_hard(question: dict, tested_data: list) -> tuple[bool | None, str | None]:
    tc = question.get("local_constraint", {}).get("transportation")
    if tc is None:
        return None, None
    for i in range(min(question["days"], len(tested_data))):
        unit = tested_data[i]
        if unit.get("transportation") and unit["transportation"] != "-":
            value = unit["transportation"]
            if tc == "no flight" and "Flight" in value:
                return False, f"The transportation should not be {tc}."
            if tc == "no self-driving" and "Self-driving" in value:
                return False, f"The transportation should not be {tc}."
    return True, None


def hard_evaluation(question: dict, tested_data: list) -> dict:
    question = _ensure_constraints_parsed(question)
    return {
        "valid_cuisine": is_valid_cuisine(question, tested_data),
        "valid_room_rule": is_valid_room_rule(question, tested_data),
        "valid_transportation": is_valid_transportation_hard(question, tested_data),
        "valid_room_type": is_valid_room_type(question, tested_data),
        "valid_cost": (bool(get_total_cost(question, tested_data) <= question["budget"]), None),
    }


# ---------------------------------------------------------------------------
# Aggregate evaluation
# ---------------------------------------------------------------------------

def evaluate_all(
    query_data_list: list[dict],
    plans: list[list[dict] | None],
    set_type: str = "train",
) -> dict:
    """
    Run full evaluation on a set of plans.

    Args:
        query_data_list: List of query metadata dicts.
        plans: List of parsed plan lists (or None for failed plans).
        set_type: "train" or "validation".

    Returns:
        Dict with all metrics and detailed breakdowns.
    """
    total = len(query_data_list)
    assert len(plans) == total, f"Mismatch: {total} queries vs {len(plans)} plans"

    # Statistics containers
    levels = ["easy", "medium", "hard"]
    days_list = [3, 5, 7]
    commonsense_stat = {lv: {d: [] for d in days_list} for lv in levels}
    hard_stat = {lv: {d: [] for d in days_list} for lv in levels}

    delivery_cnt = 0
    plan_constraint_store = []

    for idx in range(total):
        q = query_data_list[idx]
        plan = plans[idx]

        # Parse local_constraint if it's a string
        if isinstance(q.get("local_constraint"), str):
            q["local_constraint"] = eval(q["local_constraint"])

        if plan is not None:
            delivery_cnt += 1
            cs_info = commonsense_evaluation(q, plan)
        else:
            cs_info = None

        if cs_info and cs_info["is_not_absent"][0] and cs_info["is_valid_information_in_sandbox"][0]:
            h_info = hard_evaluation(q, plan)
        else:
            h_info = None

        plan_constraint_store.append({"commonsense_constraint": cs_info, "hard_constraint": h_info})
        commonsense_stat[q["level"]][q["days"]].append(cs_info)
        hard_stat[q["level"]][q["days"]].append(h_info)

    # Count constraints
    constraint_record = {lv: {d: {"house rule": 0, "cuisine": 0, "room type": 0, "transportation": 0} for d in days_list} for lv in ["medium", "hard"]}
    mapping_constraint_record = {lv: {d: {"valid_room_rule": 0, "valid_cuisine": 0, "valid_room_type": 0, "valid_transportation": 0} for d in days_list} for lv in ["medium", "hard"]}
    count_record = {lv: {d: 0 for d in days_list} for lv in levels}

    for q in query_data_list:
        count_record[q["level"]][q["days"]] += 1
        for key in ["house rule", "cuisine", "room type", "transportation"]:
            if q.get("local_constraint", {}).get(key) is not None:
                constraint_record[q["level"]][q["days"]][key] += 1
                mapping = {"house rule": "valid_room_rule", "cuisine": "valid_cuisine", "room type": "valid_room_type", "transportation": "valid_transportation"}
                mapping_constraint_record[q["level"]][q["days"]][mapping[key]] += 1

    # Final pass counting
    final_all_cnt = 0
    final_commonsense_cnt = 0
    final_hard_cnt = 0
    final_all_cnt_map = {lv: 0 for lv in levels}

    for idx in range(total):
        cs = plan_constraint_store[idx]["commonsense_constraint"]
        h = plan_constraint_store[idx]["hard_constraint"]
        if cs is None:
            continue

        cs_pass = all(
            v[0] is None or v[0]
            for v in cs.values()
        )

        if h is None:
            continue

        h_pass = all(
            v[0] is None or v[0]
            for v in h.values()
        )

        if cs_pass:
            final_commonsense_cnt += 1
        if h_pass:
            final_hard_cnt += 1
        if cs_pass and h_pass:
            final_all_cnt += 1
            final_all_cnt_map[query_data_list[idx]["level"]] += 1

    # Compute metrics — use actual number of evaluated cases as denominator
    n = total
    cs_micro_divisor = n * 8

    # For hard micro, only count constraints that are actually applicable (not None)
    h_micro_divisor = 0
    for idx in range(total):
        h = plan_constraint_store[idx]["hard_constraint"]
        if h:
            for v in h.values():
                if v[0] is not None:
                    h_micro_divisor += 1
    if h_micro_divisor == 0:
        h_micro_divisor = 1  # avoid division by zero

    # Count micro passes
    cs_micro_pass = 0
    h_micro_pass = 0
    for idx in range(total):
        cs = plan_constraint_store[idx]["commonsense_constraint"]
        h = plan_constraint_store[idx]["hard_constraint"]
        if cs:
            for v in cs.values():
                if v[0] is not None and v[0]:
                    cs_micro_pass += 1
        if h:
            for v in h.values():
                if v[0] is not None and v[0]:
                    h_micro_pass += 1

    result = {
        "Delivery Rate": delivery_cnt / n,
        "Commonsense Constraint Micro Pass Rate": cs_micro_pass / cs_micro_divisor,
        "Commonsense Constraint Macro Pass Rate": final_commonsense_cnt / n,
        "Hard Constraint Micro Pass Rate": h_micro_pass / h_micro_divisor,
        "Hard Constraint Macro Pass Rate": final_hard_cnt / n,
        "Final Pass Rate": final_all_cnt / n,
        "detail": {
            "total": total,
            "delivered": delivery_cnt,
            "final_commonsense_pass": final_commonsense_cnt,
            "final_hard_pass": final_hard_cnt,
            "final_all_pass": final_all_cnt,
            "by_level": final_all_cnt_map,
        },
    }

    return result
