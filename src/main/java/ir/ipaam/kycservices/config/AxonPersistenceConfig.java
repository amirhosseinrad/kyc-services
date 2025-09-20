package ir.ipaam.kycservices.config;

import javax.sql.DataSource;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.axonframework.eventhandling.tokenstore.jdbc.TokenSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.EventSchema;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.jdbc.SpringDataSourceConnectionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AxonPersistenceConfig {

    @Bean
    public SpringDataSourceConnectionProvider springDataSourceConnectionProvider(DataSource dataSource) {
        return new SpringDataSourceConnectionProvider(dataSource);
    }

    @Bean
    public JdbcEventStorageEngine jdbcEventStorageEngine(
            SpringDataSourceConnectionProvider connectionProvider,
            @Qualifier("eventSerializer") Serializer eventSerializer,
            @Qualifier("snapshotSerializer") Serializer snapshotSerializer,
            ObjectProvider<TransactionManager> transactionManagerProvider) {

        TransactionManager transactionManager = transactionManagerProvider
                .getIfAvailable(NoTransactionManager::instance);

        EventSchema eventSchema = EventSchema.builder()
                .eventTable("kyc_domain_event_entry")
                .snapshotTable("kyc_snapshot_event_entry")
                .build();

        return JdbcEventStorageEngine.builder()
                .connectionProvider(connectionProvider)
                .eventSerializer(eventSerializer)
                .snapshotSerializer(snapshotSerializer)
                .schema(eventSchema)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public JdbcTokenStore jdbcTokenStore(SpringDataSourceConnectionProvider connectionProvider,
            Serializer serializer,
            ObjectProvider<TransactionManager> transactionManagerProvider) {

        TransactionManager transactionManager = transactionManagerProvider
                .getIfAvailable(NoTransactionManager::instance);

        TokenSchema tokenSchema = TokenSchema.builder()
                .setTokenTableName("kyc_token_entry")
                .build();

        return JdbcTokenStore.builder()
                .connectionProvider(connectionProvider)
                .serializer(serializer)
                .schema(tokenSchema)
                .transactionManager(transactionManager)
                .build();
    }
}
