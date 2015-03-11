/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

@Provider
public final class OptionalParamProvider implements ParamConverterProvider {
    @Override
    public <T> ParamConverter<T> getConverter(Class<T> aClass, Type type, Annotation[] annotations) {
        if (aClass == Optional.class) {
            try {
                return (ParamConverter<T>) new OptionalParamConverter(type);
            } catch (NoSuchMethodException | IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    private static class OptionalParamConverter implements ParamConverter<Optional> {
        Method valueOf;
        OptionalParamConverter(Type type) throws NoSuchMethodException {
            Class classType;
            if (type instanceof Class) {
                classType = (Class) type;
            } else if (type instanceof ParameterizedType) {
                classType = (Class) ((ParameterizedType) type).getActualTypeArguments()[0];
            } else {
                throw new IllegalArgumentException("type");
            }

            try {
                valueOf = classType.getDeclaredMethod("valueOf", String.class);
            } catch (NoSuchMethodException e) {
                valueOf = classType.getDeclaredMethod("valueOf", Object.class);
            }
        }

        @Override
        public Optional fromString(String s) {
            if (s == null) {
                return Optional.empty();
            }

            try {
                Object o = valueOf.invoke(null, s);
                return Optional.of(o);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public String toString(Optional optional) {
            return String.valueOf(optional.orElse(null));
        }
    }
}
