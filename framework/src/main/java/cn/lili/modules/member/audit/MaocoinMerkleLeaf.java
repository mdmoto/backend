package cn.lili.modules.member.audit;

import cn.lili.modules.member.entity.dos.MemberPointsHistory;
import cn.hutool.crypto.digest.DigestUtil;

/**
 * Maocoin Merkle Leaf Utility
 * 
 * Provides unified logic for timestamping and hashing of Maocoin ledger entries.
 */
public class MaocoinMerkleLeaf {

    /**
     * Ensures the record has a Merkle timestamp.
     * @param history The ledger entry
     * @return The updated timestamp
     */
    public static long ensureTimestamp(MemberPointsHistory history) {
        if (history.getMerkleTimestamp() == null) {
            history.setMerkleTimestamp(System.currentTimeMillis());
        }
        return history.getMerkleTimestamp();
    }

    /**
     * Computes the leaf hash based on the standard Maocoin audit formula:
     * memberId + variablePoint + fundReserve + merkleTimestamp
     * 
     * @param history The ledger entry
     * @return The SHA-256 hex hash
     */
    public static String computeLeafHash(MemberPointsHistory history) {
        ensureTimestamp(history);
        java.math.BigDecimal fund = history.getFundReserve() != null ? 
                                   history.getFundReserve() : java.math.BigDecimal.ZERO;
                                   
        // memberId | variablePoint | fundReserve | merkleTimestamp
        String rawData = history.getMemberId() + "|" + 
                         history.getVariablePoint() + "|" + 
                         fund.toPlainString() + "|" + 
                         history.getMerkleTimestamp();
        return DigestUtil.sha256Hex(rawData);
    }
}
