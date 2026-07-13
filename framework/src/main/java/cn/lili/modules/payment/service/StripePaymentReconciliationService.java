package cn.lili.modules.payment.service;

import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import cn.lili.modules.system.entity.dto.payment.StripePaymentSetting;

/**
 * Reconciles a paid Stripe Checkout session against Stripe's charge and balance transaction.
 */
public interface StripePaymentReconciliationService {

    StripePaymentSnapshot reconcile(StripePaymentSnapshot snapshot, StripePaymentSetting setting);
}
