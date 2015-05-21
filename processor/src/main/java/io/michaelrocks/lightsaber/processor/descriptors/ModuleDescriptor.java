/*
 * Copyright 2015 Michael Rozumyanskiy
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

package io.michaelrocks.lightsaber.processor.descriptors;

import io.michaelrocks.lightsaber.InstanceProvider;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleDescriptor {
    private final Type moduleType;
    private final List<ProviderDescriptor> providers;

    public ModuleDescriptor(final Type moduleType, final List<ProviderDescriptor> providers) {
        this.moduleType = moduleType;
        this.providers = Collections.unmodifiableList(providers);
    }

    public Type getModuleType() {
        return moduleType;
    }

    public List<ProviderDescriptor> getProviders() {
        return providers;
    }

    public static class Builder {
        private final Type moduleType;
        private final List<ProviderDescriptor> providers = new ArrayList<>();
        private int providerIndex;

        public Builder(final Type moduleType) {
            this.moduleType = moduleType;
        }

        public Type getModuleType() {
            return moduleType;
        }

        public Builder addProviderField(final FieldDescriptor providerField) {
            final Type providerType = Type.getType(InstanceProvider.class);
            final ProviderDescriptor provider =
                    new ProviderDescriptor(providerType, providerField.getRawType(), providerField, moduleType);
            return addProvider(provider);
        }

        public Builder addProviderMethod(final MethodDescriptor providerMethod, final ScopeDescriptor scope) {
            providerIndex += 1;
            final Type providerType =
                    Type.getObjectType(moduleType.getInternalName() + "$$Provider$$" + providerIndex);
            final Type delegatorType = scope != null ? scope.getProviderType() : null;
            final ProviderDescriptor provider =
                    new ProviderDescriptor(providerType, providerMethod.getReturnType().getRawType(),
                            providerMethod, moduleType, delegatorType);
            return addProvider(provider);
        }

        public Builder addProvider(final ProviderDescriptor provider) {
            providers.add(provider);
            return this;
        }

        public ModuleDescriptor build() {
            return new ModuleDescriptor(moduleType, providers);
        }
    }
}