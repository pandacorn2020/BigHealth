package com.bighealth;

import com.bighealth.llm.LLMModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

public class DuplicateRemovalTest {
    String prompt = """
            请对下面提供的文本进行处理，以消除其中的重复语句，但要确保整个文本的信息完整无缺，没有信息丢失。具体要求如下：
                        
            - 仔细阅读文本，识别出重复出现的句子或段落。
            - 移除多余的重复内容，保留首次出现的实例，确保后续不再重复。
            - 确保在去除重复内容后，文本逻辑连贯，信息传达准确且完整。
            - 如果某个概念或信息通过不同的表述方式被多次提及，请合并这些信息，形成一个综合性的描述，以保证信息的唯一性和完整性。
                        
            请根据上述指示处理文本，并输出优化后的版本。注意，请不要添加任何额外的解释或说明，只需提供处理后的文本。
                        
            下面是文本：
                        
            %s
            """;
    @Test
    public void test() {
        String s = "非酒精性脂肪肝（NAFL）是非酒精性脂肪性肝病的一种形式，主要表现为肝脏脂肪堆积但无明显炎症或纤维化。 与饮酒无关的肝脏脂肪堆积，通常无明显症状 非酒精性脂肪肝（NAFLD）是一种由肝脏脂肪堆积引起的疾病，常见于肥胖、糖尿病患者，可能导致肝炎、纤维化甚至肝硬化。";
        LLMModel llmModel = new LLMModel();
        ChatLanguageModel model = llmModel.buildModel("https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-9cc9dbdd16b3488e9edb1cbad7ea695a", "qwen-plus");
        String message = prompt.formatted(s);
        ChatMessage userMessage = UserMessage.userMessage(message);
        Response<AiMessage> response = model.generate(userMessage);
        String text = response.content().text();
        System.out.println(text);
    }
}
