package ru.pathcreator.pyc.rpc.bootstrap;

import ru.pathcreator.pyc.rpc.core.RpcChannel;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RpcBootstrapEnvironmentBuilder {

    private final Map<String, RpcBootstrapChannelProfileBuilder> profiles = new LinkedHashMap<>();

    public RpcBootstrapChannelProfileBuilder channel(
            final String name,
            final RpcChannel channel
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        final RpcBootstrapChannelProfileBuilder existing = this.profiles.get(name);
        if (existing != null) {
            throw new IllegalStateException("bootstrap channel profile already registered: " + name);
        }
        final RpcBootstrapChannelProfileBuilder profile = new RpcBootstrapChannelProfileBuilder(this, name, channel);
        this.profiles.put(name, profile);
        return profile;
    }

    public RpcBootstrapEnvironment build() {
        if (this.profiles.isEmpty()) {
            throw new IllegalStateException("at least one bootstrap channel profile must be configured");
        }
        final Map<String, RpcBootstrapEnvironment.ProfileConfig> builtProfiles = new LinkedHashMap<>(this.profiles.size());
        for (final RpcBootstrapChannelProfileBuilder profile : this.profiles.values()) {
            builtProfiles.put(profile.name(), profile.buildConfig());
        }
        return new RpcBootstrapEnvironment(builtProfiles);
    }
}