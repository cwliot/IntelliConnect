/**
 * Copyright © 2023-2030 The ruanrongman Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package top.rslly.iot.utility.ai.tools;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.rslly.iot.utility.QuartzCronDateUtils;
import top.rslly.iot.utility.QuartzManager;
import top.rslly.iot.utility.ai.IcAiException;
import top.rslly.iot.utility.ai.ModelMessage;
import top.rslly.iot.utility.ai.ModelMessageRole;
import top.rslly.iot.utility.ai.Prompt;
import top.rslly.iot.utility.ai.llm.LLM;
import top.rslly.iot.utility.ai.llm.LLMFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Data
@Slf4j
public class ScheduleTool {
  @Autowired
  private Prompt prompt;
  @Value("${ai.scheduleTool-llm}")
  private String llmName;
  private String name = "scheduleTool";
  private String description = """
      Identify the time when the user wants to be reminded and convert it to a cron expression.
      Args: The time and specific needs that the user wants to be reminded of.(str)
      """;

  public String run(String question, String openid, String appid) {
    LLM llm = LLMFactory.getLLM(llmName);
    List<ModelMessage> messages = new ArrayList<>();

    ModelMessage systemMessage =
        new ModelMessage(ModelMessageRole.SYSTEM.value(), prompt.getScheduleTool());
    ModelMessage userMessage = new ModelMessage(ModelMessageRole.USER.value(), question);
    messages.add(systemMessage);
    messages.add(userMessage);
    var obj = llm.jsonChat(question, messages, true).getJSONObject("action");
    String answer = (String) obj.get("answer");
    try {
      process_llm_result(obj, openid, appid);
      return answer;
    } catch (Exception e) {
      // e.printStackTrace();
      return answer;
    }
  }

  public void process_llm_result(JSONObject jsonObject, String openid, String appid)
      throws IcAiException, ParseException {

    if (jsonObject.get("code").equals("200") || jsonObject.get("code").equals(200)) {
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      String timeStr = jsonObject.getString("time");
      String repeat = jsonObject.getString("repeat");
      String cron;
      if (repeat.equals("true")) {
        cron = jsonObject.getString("cron");
      } else
        cron = QuartzCronDateUtils.getCron(formatter.parse(timeStr));
      String uuid = UUID.randomUUID().toString();
      QuartzManager.addJob(uuid, "group-1",
          uuid, "trigger-1", RemindJob.class, cron, openid, appid);
      log.info("obj:{}", jsonObject);
      log.info("time cron{}", cron);
    } else
      throw new IcAiException("llm response error");
  }
}