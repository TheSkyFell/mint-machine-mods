package top.theillusivec4.curios.api.type.capability;

import java.util.Optional;

import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/** Stub of Curios 9.5.1+1.21.1. */
public interface ICuriosItemHandler {
    Optional<ICurioStacksHandler> getStacksHandler(String identifier);
}
