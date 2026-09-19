package tech.kayys.wayang.operator.runtime;

import tech.kayys.wayang.spi.operator.OperatorAuthorization;
import tech.kayys.wayang.spi.operator.OperatorContext;
import tech.kayys.wayang.spi.operator.OperatorPermission;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default permissive authorization guard for operators.
 * By default allows all operations, but allows explicitly denying specific permissions for tests and policy enforcement.
 */
public class PermissiveOperatorAuthorization implements OperatorAuthorization {

    private final Set<String> deniedPermissions = ConcurrentHashMap.newKeySet();

    public void deny(OperatorPermission permission) {
        if (permission != null) {
            deniedPermissions.add(permission.name());
        }
    }

    public void allow(OperatorPermission permission) {
        if (permission != null) {
            deniedPermissions.remove(permission.name());
        }
    }

    public void clear() {
        deniedPermissions.clear();
    }

    @Override
    public void require(OperatorContext context, OperatorPermission permission) {
        if (permission != null && deniedPermissions.contains(permission.name())) {
            throw new SecurityException("Permission denied: " + permission.name());
        }
    }
}
