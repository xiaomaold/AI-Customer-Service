package com.airag.modules.agent.runtime;

import com.airag.modules.agent.task.TaskStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AgentToolRegistry {

    private final List<AgentTool> agentTools;

    public Optional<AgentTool> find(TaskStep step) {
        return agentTools.stream()
                .filter(tool -> tool.supports(step))
                .findFirst();
    }
}
