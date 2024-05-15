/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.dialogue.annotations;

import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Serializer;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates an RPC endpoint.
 *
 * This annotation provides namespace for annotations for dialogue client generation.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Request {
    HttpMethod method();

    /**
     * Request path.
     *
     * Follows conjure format. Path parameter names must correspond to parameters on the annotated method.
     *
     * @see
     * <a href="https://github.com/palantir/conjure/blob/master/docs/spec/conjure_definitions.md#pathstring">Path string</a>
     */
    String path();

    /**
     * Response body {@link Deserializer}. By default this deserializer is only used for successful
     * (i.e. {@code 200 <= response.code() < 300}) responses.
     *
     * @return class that implements a zero-arg constructor to be used to deserialize the response
     */
    Class<? extends DeserializerFactory> accept() default Json.class;

    /**
     * Error handling strategy.
     *
     * @return class that implements a zero-arg constructor to be used to deserialize an error response
     */
    Class<? extends ErrorDecoder> errorDecoder() default ConjureErrorDecoder.class;

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.PARAMETER)
    @interface Body {

        /**
         * Custom body {@link Serializer}.
         *
         * @return class that implements a zero-arg constructor to be used to serialize the body. Defaults to
         * {@link Json}
         */
        Class<? extends SerializerFactory> value() default Json.class;
    }

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.PARAMETER)
    @interface Header {
        String value();

        Class<? extends ListParamEncoder<?>> encoder() default DefaultListParamEncoder.class;
    }

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.PARAMETER)
    @interface HeaderMap {
        Class<? extends MultimapParamEncoder<?>> encoder() default DefaultMultimapParamEncoder.class;
    }

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.PARAMETER)
    @interface PathParam {
        Class<? extends ParamEncoder<?>> encoder() default DefaultParamEncoder.class;

        Class<? extends ListParamEncoder<?>> listEncoder() default DefaultListParamEncoder.class;
    }

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.PARAMETER)
    @interface QueryParam {
        String value();

        Class<? extends ListParamEncoder<?>> encoder() default DefaultListParamEncoder.class;
    }

    /**
     * Similar to the Feign QueryMap serialization type. Only supports simple string -> string maps.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.PARAMETER)
    @interface QueryMap {
        Class<? extends MultimapParamEncoder<?>> encoder() default DefaultMultimapParamEncoder.class;
    }
}
