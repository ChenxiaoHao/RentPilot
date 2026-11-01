package com.rentpilot;

import com.rentpilot.dataset.DatasetImporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StartupReadinessTest {
    @Test void verifiesDatasetDuringInitializationBeforeServerLifecycleStarts() {
        AtomicInteger rowsWhenImporterInitialized = new AtomicInteger(-1);
        try (var context = new SpringApplicationBuilder(RentPilotApplication.class)
                .web(WebApplicationType.NONE)
                .initializers(application -> application.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                    @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
                        if (bean instanceof DatasetImporter) {
                            rowsWhenImporterInitialized.set(application.getBean(JdbcTemplate.class)
                                    .queryForObject("SELECT COUNT(*) FROM listing", Integer.class));
                        }
                        return bean;
                    }
                })).run("--spring.datasource.url=jdbc:h2:mem:readiness-" + UUID.randomUUID() + ";MODE=PostgreSQL",
                        "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.main.banner-mode=off")) {
            assertThat(context.getBean(JdbcTemplate.class).getDataSource()).isNotNull();
            try (var connection = context.getBean(JdbcTemplate.class).getDataSource().getConnection()) {
                assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:readiness-");
            } catch (java.sql.SQLException e) { throw new AssertionError(e); }
            assertThat(rowsWhenImporterInitialized.get()).isEqualTo(1500);
        }
    }
}
