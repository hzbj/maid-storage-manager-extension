package io.github.maidstorageextension.client;

import io.github.maidstorageextension.license.BusinessLicenseSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BusinessLicenseClientData {
    private static final Map<UUID, BusinessLicenseSnapshot.Snapshot> VALUES = new LinkedHashMap<>();

    private BusinessLicenseClientData() {
    }

    public static void accept(BusinessLicenseSnapshot.Snapshot snapshot) {
        if (snapshot != null && snapshot.id() != null) VALUES.put(snapshot.id(), snapshot);
    }

    public static BusinessLicenseSnapshot.Snapshot get(UUID id) {
        return id == null ? null : VALUES.get(id);
    }
}
