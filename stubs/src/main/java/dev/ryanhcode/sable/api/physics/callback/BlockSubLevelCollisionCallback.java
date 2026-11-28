package dev.ryanhcode.sable.api.physics.callback;

import org.joml.Vector3dc;

/** Stub of Sable 2.0.6. */
public interface BlockSubLevelCollisionCallback {
    /** removeCollision true: the ship passes through the block for this contact. */
    record CollisionResult(Vector3dc tangentMotion, boolean removeCollision) {}
}
