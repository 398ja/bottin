package xyz.tcheeric.bottin.reach.relay;

/**
 * The result of gathering follower data for one profile across a set of relays.
 *
 * @param reachCount the distinct follower count
 * @param complete   {@code true} if every queried relay reached EOSE; {@code false} if partial
 * @param anyData    {@code true} if at least one relay responded (otherwise the figure must not overwrite a prior one)
 */
public record GatherResult(long reachCount, boolean complete, boolean anyData) {
}
