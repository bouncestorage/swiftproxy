/*
 * Copyright 2015 Bounce Storage, Inc. <info@bouncestorage.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
