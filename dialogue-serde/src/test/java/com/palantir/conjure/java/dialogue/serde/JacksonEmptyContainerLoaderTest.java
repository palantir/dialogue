/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.dialogue.serde;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.example.Alias1;
import com.palantir.dialogue.example.Alias2;
import com.palantir.dialogue.example.Alias3;
import com.palantir.dialogue.example.AliasAlias1;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class JacksonEmptyContainerLoaderTest {

    private static final ObjectMapper mapper = ObjectMappers.newClientObjectMapper();
    private final JacksonEmptyContainerLoader emptyContainerDecoder = new JacksonEmptyContainerLoader(mapper);

    @Test
    public void prove_jackson_doesnt_work_out_of_the_box() throws IOException {
        assertThat(mapper.readValue("null", Alias1.class)).isNull(); // we want `Alias1.of(Optional.empty())`
        assertThat(mapper.readValue("null", Alias3.class)).isNull();
    }

    @Test
    public void capture_jackson_optional_handling_behaviour() throws IOException {
        assertThat(mapper.readValue("null", com.google.common.base.Optional.class))
                .isEqualTo(com.google.common.base.Optional.absent());
        assertThat(mapper.readValue("null", OptionalInt.class)).isEqualTo(OptionalInt.empty());
        assertThat(mapper.readValue("null", OptionalDouble.class)).isEqualTo(OptionalDouble.empty());
        assertThat(mapper.readValue("null", OptionalLong.class)).isEqualTo(OptionalLong.empty());
    }

    @Test
    public void capture_jackson_collection_behavior() throws IOException {
        assertThat(mapper.readValue("null", List.class)).isNull();
        assertThat(mapper.readValue("null", Map.class)).isNull();
        assertThat(mapper.readValue("null", Set.class)).isNull();
    }

    @Test
    public void http_204_turns_empty_body_into_alias_of_OptionalEmpty() {
        assertThat(emptyContainerDecoder.getEmptyInstance(new TypeMarker<Alias1>() {}))
                .isEqualTo(Alias1.of(Optional.empty()));
    }

    @Test
    public void http_204_turns_empty_body_into_alias_of_OptionalInt() {
        assertThat(emptyContainerDecoder.getEmptyInstance(new TypeMarker<Alias2>() {}))
                .isEqualTo(Alias2.of(OptionalInt.empty()));
    }

    @Test
    public void http_204_turns_empty_body_into_alias_of_OptionalDouble() {
        assertThat(emptyContainerDecoder.getEmptyInstance(new TypeMarker<Alias3>() {}))
                .isEqualTo(Alias3.of(OptionalDouble.empty()));
    }

    @Test
    public void http_204_can_handle_alias_of_alias_of_optional_string() {
        assertThat(emptyContainerDecoder.getEmptyInstance(new TypeMarker<AliasAlias1>() {}))
                .isEqualTo(AliasAlias1.of(Alias1.of(Optional.empty())));
    }
}
