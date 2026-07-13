package cn.lili.modules.member.audit;

import cn.hutool.crypto.digest.DigestUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Maocoin Merkle Tree Utility
 */
public class MaocoinMerkleTree {

    public static final String EMPTY_ROOT = DigestUtil.sha256Hex("MAOLLAR_EMPTY_LEDGER_V1");

    private MaocoinMerkleTree() {
    }

    public static String buildRoot(List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return EMPTY_ROOT;
        }
        if (hashes.size() == 1) {
            return hashes.get(0);
        }

        List<String> nextLevel = new ArrayList<>();
        for (int i = 0; i < hashes.size(); i += 2) {
            String left = hashes.get(i);
            String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
            nextLevel.add(DigestUtil.sha256Hex(left + right));
        }
        return buildRoot(nextLevel);
    }
}
