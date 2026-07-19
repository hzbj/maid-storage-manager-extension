package io.github.maidstorageextension.terminal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Server-world authority for terminal accounts, remembered devices and unique maid membership. */
public final class TerminalAccountData extends SavedData {
    public static final String DATA_NAME = "maid_storage_manager_extension_terminal_accounts";
    public static final int MAX_MAIDS = 32;
    public static final int MAX_MAILBOXES = 32;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,24}");
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private final Map<String, UUID> accountNames = new LinkedHashMap<>();
    private final Map<UUID, UUID> maidAccounts = new LinkedHashMap<>();

    public static final class Account {
        private final UUID id;
        private final String username;
        private byte[] passwordSalt;
        private byte[] passwordHash;
        private byte[] resetSalt = new byte[0];
        private byte[] resetHash = new byte[0];
        private boolean passwordResetRequired;
        private final LinkedHashSet<UUID> maids = new LinkedHashSet<>();
        private final List<Mailbox> mailboxes = new ArrayList<>();
        private final Map<UUID, byte[]> deviceGrants = new LinkedHashMap<>();
        private UUID selectedCourier;
        private UUID selectedDriver;
        private MailboxKey selectedMailbox;

        private Account(UUID id, String username, byte[] passwordSalt, byte[] passwordHash) {
            this.id = id;
            this.username = username;
            this.passwordSalt = passwordSalt.clone();
            this.passwordHash = passwordHash.clone();
        }

        public UUID id() { return id; }
        public String username() { return username; }
        public Set<UUID> maids() { return Set.copyOf(maids); }
        public UUID selectedCourier() { return selectedCourier; }
        public UUID selectedDriver() { return selectedDriver; }
        public MailboxKey selectedMailbox() { return selectedMailbox; }
        public List<Mailbox> mailboxes() { return List.copyOf(mailboxes); }
        public boolean passwordResetRequired() { return passwordResetRequired; }
    }

    public record Mailbox(ResourceLocation dimension, BlockPos position, UUID warehouse,
                          String warehouseName) {
        public Mailbox {
            position = position == null ? null : position.immutable();
            warehouseName = warehouseName == null ? "" : warehouseName;
        }

        public boolean sameLocation(ResourceLocation otherDimension, BlockPos otherPosition) {
            return dimension != null && position != null && dimension.equals(otherDimension)
                    && position.equals(otherPosition);
        }
    }

    public enum RegistrationResult {
        ADDED,
        ALREADY_REGISTERED,
        OWNED_BY_OTHER_ACCOUNT,
        LIMIT_REACHED,
        INVALID
    }

    public TerminalAccountData() {
    }

    public static TerminalAccountData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                TerminalAccountData::load, TerminalAccountData::new, DATA_NAME);
    }

    public static String normalizeUsername(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean validUsername(String value) {
        return value != null && USERNAME.matcher(value.trim()).matches();
    }

    public static boolean validPassword(String value) {
        if (value == null) return false;
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        return length >= 8 && length <= 128;
    }

    public Account create(String username, String password) {
        String display = username == null ? "" : username.trim();
        String normalized = normalizeUsername(display);
        if (!validUsername(display) || !validPassword(password)
                || accountNames.containsKey(normalized)) return null;
        byte[] salt = TerminalPassword.salt();
        byte[] hash = TerminalPassword.hashPassword(password.toCharArray(), salt);
        Account account = new Account(UUID.randomUUID(), display, salt, hash);
        accounts.put(account.id, account);
        accountNames.put(normalized, account.id);
        setDirty();
        return account;
    }

    public Account authenticate(String username, String password) {
        Account account = byUsername(username);
        if (account == null || password == null || account.passwordResetRequired) return null;
        byte[] actual = TerminalPassword.hashPassword(password.toCharArray(), account.passwordSalt);
        return TerminalPassword.matches(account.passwordHash, actual) ? account : null;
    }

    /** Consumes an OP-issued recovery code exactly once; a new password is required afterwards. */
    public Account authenticateReset(String username, String code) {
        Account account = byUsername(username);
        if (account == null || code == null || !account.passwordResetRequired
                || account.resetSalt.length == 0 || account.resetHash.length == 0) return null;
        byte[] actual = TerminalPassword.hashPassword(code.toCharArray(), account.resetSalt);
        if (!TerminalPassword.matches(account.resetHash, actual)) return null;
        account.resetSalt = new byte[0];
        account.resetHash = new byte[0];
        setDirty();
        return account;
    }

    public boolean issueResetCode(Account account, String code) {
        if (account == null || !validPassword(code)) return false;
        account.resetSalt = TerminalPassword.salt();
        account.resetHash = TerminalPassword.hashPassword(
                code.toCharArray(), account.resetSalt);
        account.passwordResetRequired = true;
        account.deviceGrants.clear();
        setDirty();
        return true;
    }

    public Account byId(UUID id) {
        return id == null ? null : accounts.get(id);
    }

    public Account byUsername(String username) {
        return byId(accountNames.get(normalizeUsername(username)));
    }

    public Set<UUID> registeredMaidIds() {
        return Set.copyOf(maidAccounts.keySet());
    }

    public Set<MailboxKey> registeredMailboxKeys() {
        LinkedHashSet<MailboxKey> keys = new LinkedHashSet<>();
        for (Account account : accounts.values()) {
            for (Mailbox mailbox : account.mailboxes) {
                MailboxKey key = new MailboxKey(mailbox.dimension(), mailbox.position());
                if (key.valid()) keys.add(key);
            }
        }
        return Set.copyOf(keys);
    }

    public byte[] grant(Account account, UUID terminalId) {
        if (account == null || terminalId == null) return new byte[0];
        byte[] token = TerminalPassword.token();
        account.deviceGrants.put(terminalId, TerminalPassword.hashToken(token));
        setDirty();
        return token;
    }

    public boolean verifyGrant(UUID accountId, UUID terminalId, byte[] token) {
        Account account = byId(accountId);
        byte[] expected = account == null || terminalId == null
                ? null : account.deviceGrants.get(terminalId);
        return token != null && token.length == TerminalPassword.TOKEN_BYTES
                && TerminalPassword.matches(expected, TerminalPassword.hashToken(token));
    }

    public void revoke(UUID accountId, UUID terminalId) {
        Account account = byId(accountId);
        if (account != null && account.deviceGrants.remove(terminalId) != null) setDirty();
    }

    public void revokeAll(Account account) {
        if (account == null || account.deviceGrants.isEmpty()) return;
        account.deviceGrants.clear();
        setDirty();
    }

    public boolean changePassword(Account account, String password) {
        if (account == null || !validPassword(password)) return false;
        account.passwordSalt = TerminalPassword.salt();
        account.passwordHash = TerminalPassword.hashPassword(password.toCharArray(), account.passwordSalt);
        account.resetSalt = new byte[0];
        account.resetHash = new byte[0];
        account.passwordResetRequired = false;
        account.deviceGrants.clear();
        setDirty();
        return true;
    }

    public RegistrationResult register(Account account, UUID maid) {
        if (account == null || maid == null) return RegistrationResult.INVALID;
        UUID existing = maidAccounts.get(maid);
        if (existing != null && !existing.equals(account.id)) {
            return RegistrationResult.OWNED_BY_OTHER_ACCOUNT;
        }
        if (account.maids.contains(maid)) return RegistrationResult.ALREADY_REGISTERED;
        if (account.maids.size() >= MAX_MAIDS) return RegistrationResult.LIMIT_REACHED;
        account.maids.add(maid);
        maidAccounts.put(maid, account.id);
        if (account.selectedCourier == null) account.selectedCourier = maid;
        if (account.selectedDriver == null) account.selectedDriver = maid;
        setDirty();
        return RegistrationResult.ADDED;
    }

    public boolean unregister(Account account, UUID maid) {
        if (account == null || maid == null || !account.maids.remove(maid)) return false;
        maidAccounts.remove(maid, account.id);
        if (maid.equals(account.selectedCourier)) account.selectedCourier = first(account.maids);
        if (maid.equals(account.selectedDriver)) account.selectedDriver = first(account.maids);
        setDirty();
        return true;
    }

    public boolean belongsTo(Account account, UUID maid) {
        return account != null && maid != null && account.id.equals(maidAccounts.get(maid));
    }

    public boolean selectCourier(Account account, UUID maid) {
        if (!belongsTo(account, maid)) return false;
        account.selectedCourier = maid;
        setDirty();
        return true;
    }

    public boolean selectDriver(Account account, UUID maid) {
        if (!belongsTo(account, maid)) return false;
        account.selectedDriver = maid;
        setDirty();
        return true;
    }

    public boolean registerMailbox(Account account, Mailbox mailbox) {
        if (account == null || mailbox == null || mailbox.dimension() == null
                || mailbox.position() == null || mailbox.warehouse() == null) return false;
        for (int i = 0; i < account.mailboxes.size(); i++) {
            if (account.mailboxes.get(i).sameLocation(mailbox.dimension(), mailbox.position())) {
                account.mailboxes.set(i, mailbox);
                if (account.selectedMailbox == null) {
                    account.selectedMailbox = new MailboxKey(mailbox.dimension(), mailbox.position());
                }
                setDirty();
                return true;
            }
        }
        if (account.mailboxes.size() >= MAX_MAILBOXES) return false;
        account.mailboxes.add(mailbox);
        if (account.selectedMailbox == null) {
            account.selectedMailbox = new MailboxKey(mailbox.dimension(), mailbox.position());
        }
        setDirty();
        return true;
    }

    public boolean unregisterMailbox(Account account, ResourceLocation dimension, BlockPos position) {
        if (account == null) return false;
        boolean changed = account.mailboxes.removeIf(value -> value.sameLocation(dimension, position));
        if (changed) {
            MailboxKey removed = new MailboxKey(dimension, position);
            if (removed.equals(account.selectedMailbox)) {
                account.selectedMailbox = account.mailboxes.stream()
                        .findFirst()
                        .map(mailbox -> new MailboxKey(mailbox.dimension(), mailbox.position()))
                        .orElse(null);
            }
            setDirty();
        }
        return changed;
    }

    public boolean selectMailbox(Account account, MailboxKey key) {
        if (account == null || key == null || !key.valid()
                || account.mailboxes.stream().noneMatch(
                mailbox -> mailbox.sameLocation(key.dimension(), key.position()))) return false;
        if (!key.equals(account.selectedMailbox)) {
            account.selectedMailbox = key;
            setDirty();
        }
        return true;
    }

    public boolean forceUnregister(UUID maid) {
        UUID accountId = maidAccounts.remove(maid);
        Account account = byId(accountId);
        if (account == null) return accountId != null;
        boolean changed = account.maids.remove(maid);
        if (maid.equals(account.selectedCourier)) account.selectedCourier = first(account.maids);
        if (maid.equals(account.selectedDriver)) account.selectedDriver = first(account.maids);
        if (changed || accountId != null) setDirty();
        return changed;
    }

    private static UUID first(LinkedHashSet<UUID> values) {
        return values.stream().findFirst().orElse(null);
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (Account account : accounts.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", account.id);
            tag.putString("username", account.username);
            tag.putByteArray("passwordSalt", account.passwordSalt);
            tag.putByteArray("passwordHash", account.passwordHash);
            tag.putByteArray("resetSalt", account.resetSalt);
            tag.putByteArray("resetHash", account.resetHash);
            tag.putBoolean("passwordResetRequired", account.passwordResetRequired);
            ListTag maids = new ListTag();
            for (UUID maid : account.maids) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("id", maid);
                maids.add(entry);
            }
            tag.put("maids", maids);
            ListTag grants = new ListTag();
            for (Map.Entry<UUID, byte[]> grant : account.deviceGrants.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("terminal", grant.getKey());
                entry.putByteArray("tokenHash", grant.getValue());
                grants.add(entry);
            }
            tag.put("grants", grants);
            ListTag mailboxes = new ListTag();
            for (Mailbox mailbox : account.mailboxes) {
                if (mailbox.dimension() == null || mailbox.position() == null) continue;
                CompoundTag entry = new CompoundTag();
                entry.putString("dimension", mailbox.dimension().toString());
                entry.putLong("position", mailbox.position().asLong());
                if (mailbox.warehouse() != null) entry.putUUID("warehouse", mailbox.warehouse());
                entry.putString("warehouseName", mailbox.warehouseName());
                mailboxes.add(entry);
            }
            tag.put("mailboxes", mailboxes);
            if (account.selectedCourier != null) tag.putUUID("selectedCourier", account.selectedCourier);
            if (account.selectedDriver != null) tag.putUUID("selectedDriver", account.selectedDriver);
            if (account.selectedMailbox != null) {
                tag.put("selectedMailbox", account.selectedMailbox.toTag());
            }
            list.add(tag);
        }
        root.put("accounts", list);
        return root;
    }

    public static TerminalAccountData load(CompoundTag root) {
        TerminalAccountData data = new TerminalAccountData();
        ListTag list = root.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.hasUUID("id") || !validUsername(tag.getString("username"))) continue;
            byte[] salt = tag.getByteArray("passwordSalt");
            byte[] hash = tag.getByteArray("passwordHash");
            if (salt.length < 8 || hash.length == 0) continue;
            Account account = new Account(tag.getUUID("id"), tag.getString("username"), salt, hash);
            account.resetSalt = tag.getByteArray("resetSalt");
            account.resetHash = tag.getByteArray("resetHash");
            account.passwordResetRequired = tag.getBoolean("passwordResetRequired");
            ListTag maids = tag.getList("maids", Tag.TAG_COMPOUND);
            for (int j = 0; j < maids.size() && account.maids.size() < MAX_MAIDS; j++) {
                CompoundTag entry = maids.getCompound(j);
                if (!entry.hasUUID("id")) continue;
                UUID maid = entry.getUUID("id");
                if (data.maidAccounts.putIfAbsent(maid, account.id) == null) account.maids.add(maid);
            }
            ListTag grants = tag.getList("grants", Tag.TAG_COMPOUND);
            for (int j = 0; j < grants.size(); j++) {
                CompoundTag entry = grants.getCompound(j);
                byte[] tokenHash = entry.getByteArray("tokenHash");
                if (entry.hasUUID("terminal") && tokenHash.length > 0) {
                    account.deviceGrants.put(entry.getUUID("terminal"), tokenHash);
                }
            }
            ListTag mailboxTags = tag.getList("mailboxes", Tag.TAG_COMPOUND);
            for (int j = 0; j < mailboxTags.size() && account.mailboxes.size() < MAX_MAILBOXES; j++) {
                CompoundTag entry = mailboxTags.getCompound(j);
                ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
                if (dimension == null || !entry.contains("position", Tag.TAG_LONG)
                        || !entry.hasUUID("warehouse")) continue;
                account.mailboxes.add(new Mailbox(dimension, BlockPos.of(entry.getLong("position")),
                        entry.getUUID("warehouse"), entry.getString("warehouseName")));
            }
            account.selectedCourier = account.maids.contains(tag.hasUUID("selectedCourier")
                    ? tag.getUUID("selectedCourier") : null) ? tag.getUUID("selectedCourier") : first(account.maids);
            account.selectedDriver = account.maids.contains(tag.hasUUID("selectedDriver")
                    ? tag.getUUID("selectedDriver") : null) ? tag.getUUID("selectedDriver") : first(account.maids);
            MailboxKey selectedMailbox = tag.contains("selectedMailbox", Tag.TAG_COMPOUND)
                    ? MailboxKey.fromTag(tag.getCompound("selectedMailbox")) : null;
            account.selectedMailbox = selectedMailbox != null
                    && account.mailboxes.stream().anyMatch(mailbox -> mailbox.sameLocation(
                    selectedMailbox.dimension(), selectedMailbox.position()))
                    ? selectedMailbox
                    : account.mailboxes.stream().findFirst()
                    .map(mailbox -> new MailboxKey(mailbox.dimension(), mailbox.position()))
                    .orElse(null);
            data.accounts.put(account.id, account);
            data.accountNames.put(normalizeUsername(account.username), account.id);
        }
        return data;
    }

    public List<Account> accounts() {
        return new ArrayList<>(accounts.values());
    }
}
