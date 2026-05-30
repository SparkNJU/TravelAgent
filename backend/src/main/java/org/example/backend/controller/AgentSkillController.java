package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.entity.AgentSkill;
import org.example.backend.service.AgentSkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
@CrossOrigin(origins = "*")
public class AgentSkillController {

    @Autowired
    private AgentSkillService skillService;

    @GetMapping
    public ApiResponse<List<AgentSkill>> listSkills(@RequestParam Long userId) {
        try {
            List<AgentSkill> skills = skillService.getAllSkillsForUser(userId);
            return ApiResponse.success(skills);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/active")
    public ApiResponse<List<AgentSkill>> listActiveSkills(@RequestParam Long userId) {
        try {
            List<AgentSkill> skills = skillService.getActiveSkillsForUser(userId);
            return ApiResponse.success(skills);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<AgentSkill> createSkill(
            @RequestParam Long userId,
            @RequestBody AgentSkill skill) {
        try {
            if (skill.getName() == null || skill.getName().trim().isEmpty()) {
                return ApiResponse.error("技能唯一标识(name)不能为空");
            }
            if (skill.getTitle() == null || skill.getTitle().trim().isEmpty()) {
                return ApiResponse.error("技能名称(title)不能为空");
            }
            if (skill.getDescription() == null || skill.getDescription().trim().isEmpty()) {
                return ApiResponse.error("技能描述(description)不能为空");
            }
            if (skill.getInstructions() == null || skill.getInstructions().trim().isEmpty()) {
                return ApiResponse.error("技能指令(instructions)不能为空");
            }

            // Force unique check
            if (skillService.getSkillByName(skill.getName()).isPresent()) {
                return ApiResponse.error("该技能唯一标识已存在");
            }

            AgentSkill saved = skillService.saveCustomSkill(skill, userId);
            return ApiResponse.success(saved);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<AgentSkill> updateSkill(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestBody AgentSkill skill) {
        try {
            AgentSkill updated = skillService.updateSkill(id, skill, userId);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/toggle")
    public ApiResponse<AgentSkill> toggleSkill(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam Boolean isEnabled) {
        try {
            AgentSkill toggled = skillService.toggleSkillStatus(id, isEnabled, userId);
            return ApiResponse.success(toggled);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSkill(
            @PathVariable Long id,
            @RequestParam Long userId) {
        try {
            skillService.deleteSkill(id, userId);
            return ApiResponse.success(null, "删除成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
