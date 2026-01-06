package br.eng.eaa.lambda.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public record FeedbackItem(String id, String descricao, int nota, String urgencia, String dataEnvio) {

    public static FeedbackItem from(Map<String, AttributeValue> item) {
        return new FeedbackItem(
                item.get("id").s(),
                item.get("descricao").s(),
                Integer.parseInt(item.get("nota").n()),
                item.get("urgencia").s(),
                item.get("data_envio").s()
        );
    }
}