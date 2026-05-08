package cn.lili.modules.openapi.serviceimpl;

import cn.lili.modules.openapi.service.AIService;
import org.springframework.stereotype.Service;

@Service
public class GenericAIServiceImpl implements AIService {

    @Override
    public String generateGoodsSummary(String goodsName, Double price) {
        // Here we would integrate with an actual LLM like OpenAI, DashScope, etc.
        // For now, this is a generic stub that simulates the AI response.
        
        String tier = "初级";
        if (price != null) {
            if (price > 1000) {
                tier = "高级";
            } else if (price > 100) {
                tier = "中级";
            }
        }

        return String.format("【AI Summary】该商品属于喵币收益[%s]档位。投资建议：当前市场需求稳定，建议根据自身预存款情况适量采购。", tier);
    }
}
