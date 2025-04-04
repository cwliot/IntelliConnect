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
package top.rslly.iot.utility.ai.voice;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import top.rslly.iot.utility.SseEmitterUtil;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;

@Component
@Slf4j
public class Text2audio {
  private static final String model = "cosyvoice-v1";
  private static final String voice = "longtong";
  private static SpeechSynthesisParam param;

  static class ReactCallback extends ResultCallback<SpeechSynthesisResult> {
    public CountDownLatch latch = new CountDownLatch(1);
    private final String chatId;

    ReactCallback(String chatId) {
      this.chatId = chatId;
    }

    @Override
    public void onEvent(SpeechSynthesisResult message) {
      // Write Audio to player
      if (message.getAudioFrame() != null) {
        // audioPlayer.write(message.getAudioFrame());
        JSONObject aiResponse = new JSONObject();
        var audio = message.getAudioFrame().array();
        // audio = VoiceBitChange(audio);
        aiResponse.put("audio", Base64.getEncoder().encodeToString(audio));
        SseEmitterUtil.sendMessage(chatId, aiResponse.toJSONString());
        log.info(Arrays.toString(message.getAudioFrame().array()));
      }
    }

    @Override
    public void onComplete() {
      System.out.println("synthesis onComplete!");
      SseEmitterUtil.removeUser(chatId);
      latch.countDown();
    }

    @Override
    public void onError(Exception e) {
      System.out.println("synthesis onError!");
      SseEmitterUtil.removeUser(chatId);
      e.printStackTrace();
    }

    public void waitForComplete() throws InterruptedException {
      latch.await();
    }
  }

  @Value("${ai.dashscope-key}")
  public void setApiKey(String apiKey) {
    // 填写自己的api key
    param =
        SpeechSynthesisParam.builder()
            // 若没有将API Key配置到环境变量中，需将下面这行代码注释放开，并将apiKey替换为自己的API Key
            .apiKey(apiKey)
            .model(model)
            // .format(SpeechSynthesisAudioFormat.MP3_24000HZ_MONO_256KBPS)
            .voice(voice)
            .build();
  }

  public static ByteBuffer synthesizeAndSaveAudio(String text) {

    SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
    ByteBuffer audio = synthesizer.call(text);
    log.info("requestId{}", synthesizer.getLastRequestId());
    // log.info(Arrays.toString(audio.array()));
    return audio;
  }

  public static byte[] VoiceBitChange(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("Input byte array cannot be null or empty");
    }

    File tempInputFile = null;
    File tempOutputFile = null;
    try {
      // 创建临时文件
      tempInputFile = File.createTempFile("tempInput", ".tmp");
      tempOutputFile = File.createTempFile("tempOutput", ".mp3");

      // 将二进制数组写入临时文件
      try (FileOutputStream fos = new FileOutputStream(tempInputFile)) {
        fos.write(bytes);
      }

      // Audio Attributes
      AudioAttributes audio = new AudioAttributes();
      audio.setCodec("mp3");
      audio.setBitRate(16000);
      audio.setChannels(1);
      audio.setSamplingRate(16000);

      // Encoding attributes
      EncodingAttributes attrs = new EncodingAttributes();
      attrs.setOutputFormat("mp3");
      attrs.setAudioAttributes(audio);

      // Encode
      Encoder encoder = new Encoder();
      encoder.encode(new MultimediaObject(tempInputFile), tempOutputFile, attrs);

      // 将生成的MP3文件读回为二进制数组
      return Files.readAllBytes(tempOutputFile.toPath());

    } catch (Exception e) {
      throw new RuntimeException("Error during encoding: " + e.getMessage(), e);
    } finally {
      // 安全删除临时文件
      if (tempInputFile != null && tempInputFile.exists()) {
        tempInputFile.delete();
      }
      if (tempOutputFile != null && tempOutputFile.exists()) {
        tempOutputFile.delete();
      }
    }
  }

  @Async("taskExecutor")
  public void asyncSynthesizeAndSaveAudio(String text, String chatId) {
    ReactCallback callback = new ReactCallback(chatId);
    SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, callback);
    synthesizer.call(text);
  }

}
