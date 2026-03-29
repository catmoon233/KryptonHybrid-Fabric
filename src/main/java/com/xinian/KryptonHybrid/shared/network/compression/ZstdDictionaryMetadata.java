package com.xinian.KryptonHybrid.shared.network.compression;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Metadata wrapper for pre-trained Zstd dictionaries.
 *
 * <p>This class provides a standardized format for dictionary files, including
 * version tracking, integrity validation, and creation metadata. The format is:</p>
 *
 * <pre>
 *   Magic:          4 bytes     (0x5A 0x44 0x49 0x43 = "ZDIC")
 *   Version:        2 bytes     (big-endian, currently 0x0001)
 *   Flags:          1 byte      (reserved, must be 0x00)
 *   HeaderSize:     2 bytes     (big-endian, total metadata header size in bytes)
 *   SHA256Hash:    32 bytes     (SHA-256 of plain dictionary data)
 *   PlainDictSize:  4 bytes     (big-endian, size of plain Zstd dictionary)
 *   DictID:         4 bytes     (big-endian, Zstd dictionary ID)
 *   TrainingTime:   8 bytes     (big-endian, creation timestamp in millis)
 *   SampleCount:    4 bytes     (big-endian, number of samples used for training)
 *   --
 *   Reserved:       0+ bytes    (for future extensions)
 *   --
 *   Plain Dictionary: N bytes   (raw Zstd dictionary data)
 * </pre>
 *
 * <p>For backward compatibility, plain dictionaries without this header
 * can still be loaded (metadata will be {@code null}).</p>
 */
public final class ZstdDictionaryMetadata {

    /** Magic bytes identifying a Krypton-wrapped dictionary: "ZDIC" */
    private static final int MAGIC = 0x5A444943;
    /** Current metadata format version */
    private static final short VERSION = 0x0001;
    /** Minimum header size: magic(4) + version(2) + flags(1) + headerSize(2) + hash(32) + dictSize(4) + dictID(4) + time(8) + samples(4) */
    private static final int MIN_HEADER_SIZE = 61;

    private final byte[] plainDictionary;
    private final byte[] sha256Hash;
    private final long dictID;
    private final long creationTime;
    private final int sampleCount;

    private ZstdDictionaryMetadata(byte[] plainDictionary, byte[] sha256Hash, long dictID, long creationTime, int sampleCount) {
        this.plainDictionary = plainDictionary;
        this.sha256Hash = sha256Hash;
        this.dictID = dictID;
        this.creationTime = creationTime;
        this.sampleCount = sampleCount;
    }

    /**
     * Attempts to parse dictionary metadata from a byte array.
     *
     * <p>If the input does not start with the magic bytes, returns {@code null}
     * (plain dictionary without metadata).</p>
     *
     * @param bytes the complete dictionary file bytes
     * @return parsed metadata + plain dictionary, or {@code null} if plain format
     * @throws IOException if parsing fails (corrupted metadata)
     */
    public static ZstdDictionaryMetadata tryParse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        // Check magic bytes
        int magic = ByteBuffer.wrap(bytes, 0, 4).getInt();
        if (magic != MAGIC) {
            // Not a wrapped dictionary; this is a plain dictionary
            return null;
        }

        if (bytes.length < MIN_HEADER_SIZE) {
            throw new IOException("Dictionary metadata header is truncated; minimum " + MIN_HEADER_SIZE + " bytes required, got " + bytes.length);
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.getInt(); // skip magic

        short version = buf.getShort();
        if (version != VERSION) {
            throw new IOException("Unsupported dictionary metadata version: " + version + " (expected " + VERSION + ")");
        }

        byte flags = buf.get();
        if (flags != 0) {
            throw new IOException("Invalid metadata flags: " + flags + " (must be 0x00)");
        }

        short headerSize = buf.getShort();
        if (headerSize < MIN_HEADER_SIZE || headerSize > bytes.length) {
            throw new IOException("Invalid metadata header size: " + headerSize);
        }

        // Read fixed metadata fields
        byte[] hash = new byte[32];
        buf.get(hash);

        int dictSize = buf.getInt();
        if (dictSize <= 0 || dictSize > (bytes.length - headerSize)) {
            throw new IOException("Invalid dictionary size in metadata: " + dictSize);
        }

        long dictID = buf.getInt() & 0xFFFFFFFFL;
        long creationTime = buf.getLong();
        int sampleCount = buf.getInt();

        // Extract plain dictionary
        byte[] plainDictionary = new byte[dictSize];
        System.arraycopy(bytes, headerSize, plainDictionary, 0, dictSize);

        // Validate hash
        byte[] computedHash = computeSHA256(plainDictionary);
        if (!MessageDigest.isEqual(hash, computedHash)) {
            throw new IOException("Dictionary integrity check failed: hash mismatch");
        }

        return new ZstdDictionaryMetadata(plainDictionary, hash, dictID, creationTime, sampleCount);
    }

    /**
     * Wraps a plain dictionary with metadata header.
     *
     * @param plainDict the plain Zstd dictionary bytes
     * @param dictID the Zstd dictionary ID
     * @param sampleCount number of samples used for training
     * @return complete file bytes (header + dictionary)
     * @throws IOException if wrapping fails
     */
    public static byte[] wrap(byte[] plainDict, long dictID, int sampleCount) throws IOException {
        byte[] hash = computeSHA256(plainDict);
        long creationTime = System.currentTimeMillis();
        short headerSize = MIN_HEADER_SIZE;

        ByteBuffer buf = ByteBuffer.allocate(headerSize + plainDict.length);

        // Write header
        buf.putInt(MAGIC);
        buf.putShort(VERSION);
        buf.put((byte) 0x00); // flags
        buf.putShort(headerSize);
        buf.put(hash);
        buf.putInt(plainDict.length);
        buf.putInt((int) (dictID & 0xFFFFFFFFL));
        buf.putLong(creationTime);
        buf.putInt(sampleCount);

        // Write dictionary
        buf.put(plainDict);

        return buf.array();
    }

    /**
     * Computes SHA-256 hash of the given bytes.
     */
    private static byte[] computeSHA256(byte[] data) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Returns the plain (unwrapped) dictionary bytes.
     */
    public byte[] getPlainDictionary() {
        return plainDictionary;
    }

    /**
     * Returns the SHA-256 hash of the plain dictionary.
     */
    public byte[] getHash() {
        return sha256Hash;
    }

    /**
     * Returns the Zstd dictionary ID embedded in this dictionary.
     */
    public long getDictID() {
        return dictID;
    }

    /**
     * Returns the creation time of this dictionary (milliseconds since epoch).
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Returns the number of samples used to train this dictionary.
     */
    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * Returns a human-readable description of this metadata.
     */
    @Override
    public String toString() {
        return String.format(
                "ZstdDict(id=%d, size=%d, samples=%d, created=%d)",
                dictID, plainDictionary.length, sampleCount, creationTime
        );
    }
}

