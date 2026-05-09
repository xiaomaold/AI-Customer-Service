package com.airag.modules.agent.query;

import java.util.List;

public interface KnowledgeQueryPlanner {

    List<String> buildKnowledgeSearchQueries(String question);

    List<String> buildDocumentSearchQueries(String question, String documentName);

    boolean isEnumeratingDocumentQuestion(String question);
}
