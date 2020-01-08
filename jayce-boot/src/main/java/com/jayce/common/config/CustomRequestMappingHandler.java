package com.jayce.common.config;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodIntrospector;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CustomRequestMappingHandler extends RequestMappingHandlerMapping {

    @Override
    protected void detectHandlerMethods(Object handler) {
        Class<?> handlerType = (handler instanceof String ?
                obtainApplicationContext().getType((String) handler) : handler.getClass());

        if (handlerType != null) {
            Class<?> userType = ClassUtils.getUserClass(handlerType);
            Map<Method, RequestMappingInfo> methods = MethodIntrospector.selectMethods(userType,
                    (MethodIntrospector.MetadataLookup<RequestMappingInfo>) method -> {
                        try {
                            return getMappingForMethod(method, userType);
                        } catch (Throwable ex) {
                            throw new IllegalStateException("Invalid mapping on handler class [" +
                                    userType.getName() + "]: " + method, ex);
                        }
                    });
            if (logger.isTraceEnabled()) {
                logger.trace(formatMappings(userType, methods));
            }
            methods.forEach((method, mapping) -> {
                Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
                if (!hasUrl(mapping)) {
                    registerHandlerMethod(handler, invocableMethod, mapping);
                }
            });
        }
    }

    private String formatMappings(Class<?> userType, Map<Method, RequestMappingInfo> methods) {
        String formattedType = Arrays.stream(ClassUtils.getPackageName(userType).split("\\."))
                .map(p -> p.substring(0, 1))
                .collect(Collectors.joining(".", "", ".")) + userType.getSimpleName();
        Function<Method, String> methodFormatter = method -> Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
        return methods.entrySet().stream()
                .map(e -> {
                    Method method = e.getKey();
                    return e.getValue() + ": " + method.getName() + methodFormatter.apply(method);
                })
                .collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
    }

    private boolean hasUrl(RequestMappingInfo mapping) {
        Set<RequestMethod> mappingSet = mapping.getMethodsCondition().getMethods();
        boolean flag = false;
        //新逻辑，去重复同名接口
        Set<RequestMappingInfo> requestMappingInfos = getHandlerMethods().keySet();
        if (null != requestMappingInfos && requestMappingInfos.size() > 0) {
            if (requestMappingInfos.stream().filter(fl -> fl.getPatternsCondition().equals(mapping.getPatternsCondition())).toArray().length > 0) {
                flag = true;
            }
        }
        //原先逻辑
        /*for(RequestMappingInfo info : getHandlerMethods().keySet()) {
        Set<RequestMethod> infoSet = info.getMethodsCondition().getMethods();
            if(info.getPatternsCondition().getPatterns().
                    equals(mapping.getPatternsCondition().getPatterns())
                    && ((mappingSet.size() == infoSet.size())))
            {
                Set<RequestMethod> totalset =new HashSet<>(8);
                totalset.addAll(mappingSet);
                totalset.addAll(infoSet);
                if(totalset.size() == infoSet.size()) {
                    flag = true;
                }
            }
        }*/
        return flag;
    }

    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        return null;
    }
}
