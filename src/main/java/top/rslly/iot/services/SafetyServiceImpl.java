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
package top.rslly.iot.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.rslly.iot.models.*;
import top.rslly.iot.param.request.ProductModel;
import top.rslly.iot.param.request.User;
import top.rslly.iot.utility.JwtTokenUtil;

import java.util.List;

@Service
@Slf4j
public class SafetyServiceImpl implements SafetyService {
  @Autowired
  private WxProductBindServiceImpl wxProductBindService;
  @Autowired
  private ProductModelServiceImpl productModelService;
  @Autowired
  private ProductDeviceServiceImpl productDeviceService;
  @Autowired
  private ProductEventServiceImpl productEventService;
  @Autowired
  private UserProductBindServiceImpl userProductBindService;
  @Autowired
  private ProductFunctionServiceImpl productFunctionService;
  @Autowired
  private EventDataServiceImpl eventDataService;
  @Autowired
  private ProductDataServiceImpl productDataService;
  @Autowired
  private UserServiceImpl userService;
  @Autowired
  private WxUserServiceImpl wxUserService;

  @Override
  public boolean controlAuthorizeModel(String token, int modelId) {
    List<ProductModelEntity> productModelEntityList = productModelService.findAllById(modelId);
    if (productModelEntityList.isEmpty())
      throw new NullPointerException("modelId not found!");
    return this.controlAuthorizeProduct(token,
        productModelEntityList.get(0).getProductId());
  }

  @Override
  public boolean controlAuthorizeDevice(String token, int deviceId) {
    List<ProductDeviceEntity> productDeviceEntityList = productDeviceService.findAllById(deviceId);
    if (productDeviceEntityList.isEmpty())
      throw new NullPointerException("deviceId not found!");
    return this.controlAuthorizeModel(token,
        productDeviceEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeFunction(String token, int functionId) {
    List<ProductFunctionEntity> productFunctionEntityList =
        productFunctionService.findAllById(functionId);
    if (productFunctionEntityList.isEmpty())
      throw new NullPointerException("functionId not found!");
    return this.controlAuthorizeModel(token,
        productFunctionEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeEvent(String token, int eventId) {
    List<ProductEventEntity> productEventEntityList = productEventService.findAllById(eventId);
    if (productEventEntityList.isEmpty())
      throw new NullPointerException("eventId not found!");
    return this.controlAuthorizeModel(token,
        productEventEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeEventData(String token, int eventDataId) {
    List<EventDataEntity> eventDataEntityList = eventDataService.findAllById(eventDataId);
    if (eventDataEntityList.isEmpty())
      throw new NullPointerException("eventDataId not found!");
    return this.controlAuthorizeModel(token,
        eventDataEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeProductData(String token, int productDataId) {
    List<ProductDataEntity> productDataEntityList = productDataService.findAllById(productDataId);
    if (productDataEntityList.isEmpty())
      throw new NullPointerException("productDataId not found!");
    return this.controlAuthorizeModel(token,
        productDataEntityList.get(0).getModelId());
  }

  @Override
  public boolean controlAuthorizeProduct(String token, int productId) {
    String token_deal = token.replace(JwtTokenUtil.TOKEN_PREFIX, "");
    String role = JwtTokenUtil.getUserRole(token_deal);
    String username = JwtTokenUtil.getUsername(token_deal);
    log.info("role {}", role);
    if (role.equals("ROLE_" + "wx_user")) {
      if (wxUserService.findAllByName(username).isEmpty()) {
        return false;
      }
      String openid = wxUserService.findAllByName(username).get(0).getOpenid();
      // log.info("productId{}",productId);
      return !wxProductBindService.findByOpenidAndProductId(openid, productId).isEmpty();
    } else if (!role.equals("[ROLE_admin]")) {
      var userList = userService.findAllByUsername(username);
      if (userList.isEmpty())
        return false;
      int userId = userList.get(0).getId();
      return !userProductBindService.findAllByUserIdAndProductId(userId, productId).isEmpty();
    }
    return true;
  }
}
