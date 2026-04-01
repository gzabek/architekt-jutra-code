package pl.devstyle.aj.core.error;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entityType, Long id) {
        super(entityType + " with id " + id + " not found");
    }

    public EntityNotFoundException(String entityType, String id) {
        super(entityType + " with id " + id + " not found");
    }
}
