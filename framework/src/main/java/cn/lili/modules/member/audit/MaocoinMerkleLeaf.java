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
     * version + memberId + pointType + variablePoint + beforePoint + point + fundReserve + bizId + merkleTimestamp
     * 
     * @param history The ledger entry
     * @return The SHA-256 hex hash
     */
    public static String computeLeafHash(MemberPointsHistory history) {
        ensureTimestamp(history);
        java.math.BigDecimal fund = history.getFundReserve() != null ? 
                                   history.getFundReserve() : java.math.BigDecimal.ZERO;
                                   
        String rawData = "MAOCOIN_LEAF_V2|" +
                         value(history.getMemberId()) + "|" +
                         value(history.getPointType()) + "|" +
                         value(history.getVariablePoint()) + "|" +
                         value(history.getBeforePoint()) + "|" +
                         value(history.getPoint()) + "|" +
                         fund.toPlainString() + "|" +
                         value(history.getBizId()) + "|" +
                         history.getMerkleTimestamp();
        return DigestUtil.sha256Hex(rawData);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
