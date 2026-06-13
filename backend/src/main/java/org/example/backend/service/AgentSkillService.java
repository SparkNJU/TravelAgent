package org.example.backend.service;

import jakarta.annotation.PostConstruct;
import org.example.backend.entity.AgentSkill;
import org.example.backend.repository.AgentSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AgentSkillService {

    private static final Logger log = LoggerFactory.getLogger(AgentSkillService.class);

    @Autowired
    private AgentSkillRepository skillRepository;

    @PostConstruct
    public void seedSkills() {
        try {
            log.info("Checking and seeding default Agent skills...");
            
            // Check budget-optimizer
            if (skillRepository.findByName("budget-optimizer").isEmpty()) {
                AgentSkill budgetSkill = new AgentSkill(
                    "budget-optimizer",
                    "预算优化专家",
                    "用户提出“省钱”、“预算有限”、“性价比高”或“超出预算/超支”",
                    "你已激活“预算优化专家”技能。在后续规划行程中，请严格遵守以下规则：\n" +
                    "1. 将总预算按照 3:2:3:2（住宿、交通、门票/美食、预备金）的黄金法则合理分配并展示分析。\n" +
                    "2. 识别用户原本行程中的高消费收费景点，并提供 1-2 个免费或超高性价比的平替方案（如高档景观露台 -> 免费的日落观景台）。\n" +
                    "3. 提供针对该目的地的省钱特异性避坑贴士。",
                    true,
                    null
                );
                skillRepository.save(budgetSkill);
                log.info("Seeded default skill: budget-optimizer");
            }

            // Check packing-helper
            if (skillRepository.findByName("packing-helper").isEmpty()) {
                AgentSkill packingSkill = new AgentSkill(
                    "packing-helper",
                    "智能出行行李箱",
                    "用户询问“带什么衣服”、“需要准备什么”、“行李清单/物品清单”",
                    "你已激活“智能出行行李箱”技能。请根据目的地、当前天气预报和出行天数，为用户制作一份极其精细的分类行李清单，包含：\n" +
                    "1. 穿搭指南：依据天气温度提供实用的多层穿衣法。\n" +
                    "2. 必备证件与电子产品清单。\n" +
                    "3. 特殊物品：如雨具（雨天）、防晒霜（晴天）、必备应急药品。\n" +
                    "4. 清单以 Markdown 待办列表 `[ ]` 形式呈现，方便用户直接对照打勾。",
                    true,
                    null
                );
                skillRepository.save(packingSkill);
                log.info("Seeded default skill: packing-helper");
            }

            // Check skill-creator
            if (skillRepository.findByName("skill-creator").isEmpty()) {
                AgentSkill creatorSkill = new AgentSkill(
                    "skill-creator",
                    "技能创建专家",
                    "用户想要创建、定制、设计或修改一个新的 AI 规划技能",
                    "你已激活“技能创建专家”技能。在接下来的对话中，你的任务是协助用户设计并创建新的自定义技能：\n" +
                    "1. 仔细倾听或引导用户提供以下必填信息：\n" +
                    "   - 英文唯一标识 (name): 必须是纯英文，使用连字符分隔单词（如 honeymoon-advisor, food-expert）\n" +
                    "   - 中文名称 (title): 清晰易读的中文名称（如 蜜月浪漫顾问, 美食寻味专家）\n" +
                    "   - 激活条件 (description): 详尽描述 Agent 在什么场景下激活此技能（例如：“用户提到去哪里吃饭、寻找特色美食或推荐餐厅”）\n" +
                    "   - 详细指令 (instructions): 具体的 Prompt，指导 Agent 在该技能激活时该怎么做（支持 Markdown 格式）\n" +
                    "2. 收集或补充好所有要素后，调用 `create_skill` 工具，传入上述 4 个参数。\n" +
                    "3. 等待工具执行结果。成功创建后，以优雅的 Markdown 格式向用户反馈该技能已成功创建，展示其名称、标识及激活条件，并提示用户可以在“技能工坊”中看到并管理它。",
                    true,
                    null
                );
                skillRepository.save(creatorSkill);
                log.info("Seeded default skill: skill-creator");
            }

            // Update existing descriptions if needed to match new clean format
            List<AgentSkill> allSeeded = skillRepository.findAll();
            for (AgentSkill s : allSeeded) {
                boolean updated = false;
                if (s.getUserId() == null && s.getDescription() != null && s.getDescription().startsWith("当") && s.getDescription().endsWith("时激活。")) {
                    String cleanDesc = s.getDescription().substring(1, s.getDescription().length() - 4);
                    s.setDescription(cleanDesc);
                    updated = true;
                }
                if ("skill-creator".equals(s.getName())) {
                    String targetInstructions = "你已激活“技能创建专家”技能。在接下来的对话中，你的任务是协助用户设计并创建新的自定义技能：\n" +
                            "1. 仔细倾听或引导用户提供以下必填信息：\n" +
                            "   - 英文唯一标识 (name): 必须是纯英文，使用连字符分隔单词（如 honeymoon-advisor, food-expert）\n" +
                            "   - 中文名称 (title): 清晰易读的中文名称（如 蜜月浪漫顾问, 美食寻味专家）\n" +
                            "   - 激活条件 (description): 详尽描述 Agent 在什么场景下激活此技能（例如：“用户提到去哪里吃饭、寻找特色美食或推荐餐厅”）\n" +
                            "   - 详细指令 (instructions): 具体的 Prompt，指导 Agent 在该技能激活时该怎么做（支持 Markdown 格式）\n" +
                            "2. 收集或补充好所有要素后，调用 `create_skill` 工具，传入上述 4 个参数。\n" +
                            "3. 等待工具执行结果。成功创建后，以优雅的 Markdown 格式向用户反馈该技能已成功创建，展示其名称、标识及激活条件，并提示用户可以在“技能工坊”中看到并管理它。";
                    if (!targetInstructions.equals(s.getInstructions())) {
                        s.setInstructions(targetInstructions);
                        updated = true;
                    }
                }
                if (updated) {
                    skillRepository.save(s);
                    log.info("Updated existing system skill: " + s.getName());
                }
            }

            log.info("Agent skills checked successfully.");
        } catch (Exception e) {
            log.error("Failed to seed default agent skills", e);
        }
    }

    public List<AgentSkill> getActiveSkillsForUser(Long userId) {
        return skillRepository.findActiveSkillsForUser(userId);
    }

    public List<AgentSkill> getAllSkillsForUser(Long userId) {
        return skillRepository.findAllSkillsForUser(userId);
    }

    public Optional<AgentSkill> getSkillById(Long id) {
        return skillRepository.findById(id);
    }

    public Optional<AgentSkill> getSkillByName(String name) {
        return skillRepository.findByName(name);
    }

    public AgentSkill saveCustomSkill(AgentSkill skill, Long userId) {
        skill.setUserId(userId);
        return skillRepository.save(skill);
    }

    public AgentSkill updateSkill(Long id, AgentSkill updatedData, Long userId) {
        AgentSkill existing = skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该技能不存在"));

        // If it's a built-in skill, anyone can toggle it, but only admins or system can edit content
        // For a single user / simple system, we just allow modifications
        if (existing.getUserId() != null && !existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改他人的自定义技能");
        }

        if (updatedData.getTitle() != null) {
            existing.setTitle(updatedData.getTitle());
        }
        if (updatedData.getDescription() != null) {
            existing.setDescription(updatedData.getDescription());
        }
        if (updatedData.getInstructions() != null) {
            existing.setInstructions(updatedData.getInstructions());
        }
        if (updatedData.getIsEnabled() != null) {
            existing.setIsEnabled(updatedData.getIsEnabled());
        }
        if (updatedData.getScriptsCode() != null) {
            existing.setScriptsCode(updatedData.getScriptsCode());
        }
        if (updatedData.getReferencesData() != null) {
            existing.setReferencesData(updatedData.getReferencesData());
        }

        return skillRepository.save(existing);
    }

    public void deleteSkill(Long id, Long userId) {
        AgentSkill existing = skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该技能不存在"));

        if (existing.getUserId() == null) {
            throw new RuntimeException("不能删除系统内置技能");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人的自定义技能");
        }

        skillRepository.delete(existing);
    }

    public AgentSkill toggleSkillStatus(Long id, Boolean isEnabled, Long userId) {
        AgentSkill existing = skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该技能不存在"));

        // Allow toggling even system skills for convenience
        existing.setIsEnabled(isEnabled);
        return skillRepository.save(existing);
    }
}
