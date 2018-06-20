/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.contentpacks.facades;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graylog2.contentpacks.model.ModelId;
import org.graylog2.contentpacks.model.ModelType;
import org.graylog2.contentpacks.model.ModelTypes;
import org.graylog2.contentpacks.model.entities.Entity;
import org.graylog2.contentpacks.model.entities.EntityDescriptor;
import org.graylog2.contentpacks.model.entities.EntityExcerpt;
import org.graylog2.contentpacks.model.entities.EntityV1;
import org.graylog2.contentpacks.model.entities.EntityWithConstraints;
import org.graylog2.contentpacks.model.entities.GrokPatternEntity;
import org.graylog2.contentpacks.model.entities.references.ValueReference;
import org.graylog2.database.NotFoundException;
import org.graylog2.grok.GrokPattern;
import org.graylog2.grok.GrokPatternService;
import org.graylog2.plugin.database.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GrokPatternFacade implements EntityFacade<GrokPattern> {
    private static final Logger LOG = LoggerFactory.getLogger(GrokPatternFacade.class);

    public static final ModelType TYPE = ModelTypes.GROK_PATTERN;

    private final ObjectMapper objectMapper;
    private final GrokPatternService grokPatternService;

    @Inject
    public GrokPatternFacade(ObjectMapper objectMapper, GrokPatternService grokPatternService) {
        this.objectMapper = objectMapper;
        this.grokPatternService = grokPatternService;
    }

    @Override
    public EntityWithConstraints encode(GrokPattern grokPattern) {
        final GrokPatternEntity grokPatternEntity = GrokPatternEntity.create(
                ValueReference.of(grokPattern.name()),
                ValueReference.of(grokPattern.pattern()));
        final JsonNode data = objectMapper.convertValue(grokPatternEntity, JsonNode.class);
        final EntityV1 entity = EntityV1.builder()
                .id(ModelId.of(grokPattern.id()))
                .type(ModelTypes.GROK_PATTERN)
                .data(data)
                .build();
        return EntityWithConstraints.create(entity);
    }

    @Override
    public GrokPattern decode(Entity entity, Map<String, ValueReference> parameters, String username) {
        if (entity instanceof EntityV1) {
            return decodeEntityV1((EntityV1) entity, parameters);
        } else {
            throw new IllegalArgumentException("Unsupported entity version: " + entity.getClass());
        }
    }

    private GrokPattern decodeEntityV1(EntityV1 entity, Map<String, ValueReference> parameters) {
        final GrokPatternEntity grokPatternEntity = objectMapper.convertValue(entity.data(), GrokPatternEntity.class);
        final GrokPattern grokPattern = GrokPattern.create(
                grokPatternEntity.name().asString(parameters),
                grokPatternEntity.pattern().asString(parameters));

        try {
            return grokPatternService.save(grokPattern);
        } catch (ValidationException e) {
            throw new RuntimeException("Couldn't create grok pattern " + grokPattern.name());
        }
    }

    @Override
    public EntityExcerpt createExcerpt(GrokPattern grokPattern) {
        return EntityExcerpt.builder()
                .id(ModelId.of(grokPattern.id()))
                .type(ModelTypes.GROK_PATTERN)
                .title(grokPattern.name())
                .build();
    }

    @Override
    public Set<EntityExcerpt> listEntityExcerpts() {
        return grokPatternService.loadAll().stream()
                .map(this::createExcerpt)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<EntityWithConstraints> collectEntity(EntityDescriptor entityDescriptor) {
        final ModelId modelId = entityDescriptor.id();
        try {
            final GrokPattern grokPattern = grokPatternService.load(modelId.id());
            return Optional.of(encode(grokPattern));
        } catch (NotFoundException e) {
            LOG.debug("Couldn't find grok pattern {}", entityDescriptor, e);
            return Optional.empty();
        }
    }
}