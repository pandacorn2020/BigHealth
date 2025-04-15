package com.bighealth.llm;

import com.bighealth.entity.CommunityData;
import com.bighealth.entity.KGEntity;
import com.bighealth.entity.KGGraph;
import com.bighealth.entity.KGRelationship;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.jgrapht.Graph;

import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Callable;

public class KgCommunityTask implements Callable<CommunityData> {
    private String communityTemplate;

    private KGGraph kgGraph;
    private Graph<KGEntity, KGRelationship> graph;

    private Set<KGEntity> cluster;

    private CommunityData communityData;

    private ChatLanguageModel model;

    public KgCommunityTask(String communityTemplate,
                           KGGraph kgGraph,
                           Graph<KGEntity, KGRelationship> graph,
                           Set<KGEntity> cluster,
                  ChatLanguageModel model) {
        this.communityTemplate = communityTemplate;
        this.kgGraph = kgGraph;
        this.graph = graph;
        this.cluster = cluster;
        this.model = model;
    }

    public CommunityData getCommunityData() {
        return communityData;
    }

    @Override
    public CommunityData call() {
        try {

            StringJoiner joiner = new StringJoiner("\n");
            StringJoiner edgeJoiner = new StringJoiner("\n");
            for (KGEntity node : cluster) {
                joiner.add(node.toString());
                for (KGRelationship edge : graph.edgesOf(node)) {
                    KGEntity source = kgGraph.getEntity(edge.getSource());
                    KGEntity target = kgGraph.getEntity(edge.getTarget());
                    if (cluster.contains(source) && cluster.contains(target)) {
                        edgeJoiner.add(edge.toString());
                    }
                }
            }
            String message = String.format(communityTemplate,
                    joiner.toString(), edgeJoiner.toString());

            ChatMessage userMessage = UserMessage.userMessage(message);
            Response<AiMessage> response = model.generate(userMessage);
            String text = response.content().text();
            text = removeTag(text);
            this.communityData = CommunityData.read(text);
            return communityData;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String removeTag(String text) {
        int startIndex = text.indexOf("```json");
        if (startIndex >= 0) {
            int endIndex = text.indexOf("```", startIndex + 1);
            text = text.substring(startIndex + "```json".length(), endIndex);
        }
        startIndex = text.indexOf("{");
        int index1 = text.indexOf("}");
        if (startIndex >= 0 && index1 >= 0 && index1 > startIndex) {
            return text.substring(startIndex, index1 + 1);
        }
        return text;
    }
}