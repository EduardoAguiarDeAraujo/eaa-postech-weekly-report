package br.eng.eaa.lambda;

import br.eng.eaa.lambda.model.FeedbackItem;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named("report")
public class GenerateReportHandler implements RequestHandler<ScheduledEvent, Void> {

    @Inject
    DynamoDbClient dynamoDB;

    @Inject
    SesClient ses;

    @ConfigProperty(name = "dynamodb.table.name")
    String tableName;

    @ConfigProperty(name = "email.from")
    String from;

    @ConfigProperty(name = "email.to")
    String to;

    @Override
    public Void handleRequest(ScheduledEvent scheduledEvent, Context context) {

//        String lastWeek = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        String lastWeek = Instant.now().minus(1, ChronoUnit.HOURS).toString();

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("dataEnvio > :lastWeek")
                .expressionAttributeValues(Map.of(":lastWeek", AttributeValue.builder().s(lastWeek).build()))
                .build();

        List<FeedbackItem> items = dynamoDB.scanPaginator(scanRequest)
                .items()
                .stream()
                .map(FeedbackItem::from)
                .collect(Collectors.toList());

        if (items.isEmpty()) {
            sendReport("Nenhum feedback na última semana.");
            return null;
        }

        // Calcular métricas
        double mediaNotas = items.stream().mapToInt(FeedbackItem::nota).average().orElse(0.0);
        Map<String, Long> countPorDia = items.stream().collect(Collectors.groupingBy(FeedbackItem::dataEnvio, Collectors.counting()));
        Map<String, Long> countPorUrgencia = items.stream().collect(Collectors.groupingBy(FeedbackItem::urgencia, Collectors.counting()));
        List<String> descricoes = items.stream().map(FeedbackItem::descricao).toList();

        // Gerar relatório
        StringBuilder report = new StringBuilder();
        report.append("Relatório Semanal de Feedbacks:\n");
        report.append("Média de notas: ").append(String.format("%.2f", mediaNotas)).append("\n");
        report.append("Quantidade por dia:\n");
        countPorDia.forEach((dia, count) -> report.append(dia).append(": ").append(count).append("\n"));
        report.append("Quantidade por urgência:\n");
        countPorUrgencia.forEach((urg, count) -> report.append(urg).append(": ").append(count).append("\n"));
        report.append("Descrições:\n");
        descricoes.forEach(desc -> report.append("- ").append(desc).append("\n"));

        sendReport(report.toString());
        return null;    }

    private void sendReport(String content) {
        ses.sendEmail(SendEmailRequest.builder()
                .source(from)
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data("Relatório Semanal de Feedbacks").build())
                        .body(Body.builder()
                                .text(Content.builder().data(content).build())
                                .build())
                        .build())
                .build());
    }
}
