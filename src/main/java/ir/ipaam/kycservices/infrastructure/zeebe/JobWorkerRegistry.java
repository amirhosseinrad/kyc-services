package ir.ipaam.kycservices.infrastructure.zeebe;

import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class JobWorkerRegistry {
    private final ListableBeanFactory beanFactory;
    private Set<String> registeredTypes;
    public JobWorkerRegistry(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
    private void ensureScanned() {
        if (registeredTypes == null) {
            registeredTypes = new HashSet<>();
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                Object bean = beanFactory.getBean(beanName); // triggers only when used
                for (Method m : bean.getClass().getMethods()) {
                    if (m.isAnnotationPresent(io.camunda.zeebe.spring.client.annotation.JobWorker.class)) {
                        JobWorker annotation = m.getAnnotation(JobWorker.class);
                        registeredTypes.add(annotation.type());
                    }
                }
            }
        }
    }

    public boolean isRegistered(String jobType) {
        ensureScanned();
        return registeredTypes.contains(jobType);
    }

    public Set<String> getRegisteredTypes() {
        ensureScanned();
        return Collections.unmodifiableSet(registeredTypes);
    }
}
