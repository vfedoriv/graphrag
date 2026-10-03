package io.github.vfedoriv.graphrag.ai.contracts;

/** Profile existence, default and immutable execution facts. */
public interface AiProfileAccess {
    ProfileFacts require(String profileId);
    ProfileFacts defaultFacts();
    ProfileView inspect(String profileId);
}
