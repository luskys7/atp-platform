<template>
  <div class="page-container picker-page">
    <!-- 设备选择（无 deviceId 时） -->
    <template v-if="!deviceId">
      <PageHeader title="控件获取" subtitle="选择在线安卓设备，在投屏画面点击控件即可获取定位信息，无需新建录制用例">
        <template #actions>
          <el-button :loading="devicesLoading" @click="loadDevices">刷新设备</el-button>
          <el-button type="warning" plain :loading="rebootingOffline" @click="rebootAllOffline">重启离线设备</el-button>
          <el-button type="primary" plain @click="$router.push('/controls')">进入控件库</el-button>
        </template>
      </PageHeader>

      <div class="status-banner" :class="hubBanner.tone">
        <el-icon><component :is="hubBanner.icon" /></el-icon>
        <span class="status-banner__text">{{ hubBanner.text }}</span>
        <div v-if="hubBanner.tone === 'danger'" class="status-banner__actions">
          <el-button size="small" type="warning" :loading="rebootingOffline" @click="rebootAllOffline">一键重启全部设备</el-button>
          <el-button size="small" type="primary" :loading="syncingUsb" @click="syncUsbFromHub">同步 USB 接入新设备</el-button>
        </div>
      </div>

      <AppCard title="可用设备" :hover="false">
        <el-table v-loading="devicesLoading" :data="androidDevices" stripe empty-text="">
          <el-table-column label="设备名称" min-width="140">
            <template #default="{ row }">
              <span class="dev-name">{{ row.name || row.model || row.serial_number || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="设备序列号" min-width="160">
            <template #default="{ row }">
              <span class="dev-serial">{{ row.serial_number || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="屏幕分辨率" width="130">
            <template #default="{ row }">
              {{ row.screen_width && row.screen_height ? `${row.screen_width}×${row.screen_height}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="设备状态" width="110">
            <template #default="{ row }">
              <el-tag
                size="small"
                round
                effect="plain"
                class="status-tag"
                :class="isDeviceOnline(row) ? 'is-online' : 'is-offline'"
              >
                {{ isDeviceOnline(row) ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-tooltip
                :disabled="canStartPick(row)"
                content="设备处于离线状态，无法开启控件拾取，请重启设备后重试"
                placement="top"
              >
                <span>
                  <el-button
                    type="primary"
                    size="small"
                    :disabled="!canStartPick(row)"
                    @click="openPicker(row.id)"
                  >
                    开始拾取
                  </el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!devicesLoading && !androidDevices.length" class="hub-empty">
          <p>暂无已接入的测试设备</p>
          <div class="hub-empty__actions">
            <el-button type="primary" :loading="syncingUsb" @click="syncUsbFromHub">同步 USB 设备</el-button>
            <el-button v-if="userStore.isAdmin" @click="showWhitelistDialog = true">添加设备白名单</el-button>
          </div>
        </div>
      </AppCard>

      <el-dialog v-model="showWhitelistDialog" title="添加设备白名单" width="480px" destroy-on-close>
        <el-form :model="whitelistForm" label-width="100px">
          <el-form-item label="序列号" required><el-input v-model="whitelistForm.serial_number" /></el-form-item>
          <el-form-item label="平台" required>
            <el-select v-model="whitelistForm.platform" style="width:100%">
              <el-option label="Android" value="android" /><el-option label="iOS" value="ios" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注"><el-input v-model="whitelistForm.remark" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showWhitelistDialog = false">取消</el-button>
          <el-button type="primary" @click="addWhitelistFromHub">确认</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- 控件拾取工作台 -->
    <template v-else>
      <PageHeader
        :title="`控件获取 · ${device?.model || device?.name || device?.serial_number || `#${deviceId}`}`"
        subtitle="在投屏画面点击控件即可获取定位信息；识别前建议先刷新 UI 树"
      >
        <template #actions>
          <el-tag v-if="connected" type="success" size="small">已连接</el-tag>
          <el-tag v-else-if="connecting" type="warning" size="small">连接中</el-tag>
          <el-tag v-if="connected && fps > 0" type="info" size="small" style="margin-left:8px">
            {{ fps }} FPS
          </el-tag>
          <el-button style="margin-left:12px" @click="goHub">切换设备</el-button>
          <el-button @click="$router.push('/controls')">进入控件库</el-button>
          <el-button v-if="pickReturnTo" type="primary" @click="goReturn">返回审阅</el-button>
        </template>
      </PageHeader>

      <div v-if="pickReviewHint" class="review-banner">
        正在为审阅步骤 <strong>#{{ pickStepIndex + 1 }}</strong> 补定位 — 点击目标控件后点「应用到审阅步骤」
      </div>

      <div class="picker-workbench">
        <aside class="screen-panel">
          <AppCard :hover="false" class="screen-card">
            <template #header>
              <div class="screen-card-head">
                <span>设备投屏拾取画面 - {{ device?.model || device?.name || device?.serial_number || '未知设备' }}</span>
                <div class="screen-card-actions">
                  <el-button size="small" type="danger" plain @click="endPickSession">结束拾取</el-button>
                  <el-button size="small" :disabled="!hasFrame" @click="captureScreenshot">截取当前页面截图</el-button>
                  <el-button size="small" :disabled="!currentPick" @click="clearCurrentPick">清空上一次拾取的控件信息</el-button>
                </div>
              </div>
            </template>
            <div class="screen-wrap" ref="screenWrapRef">
              <div class="screen-device">
                <div class="screen-frame" :style="screenFrameStyle">
                  <canvas
                    ref="canvasRef"
                    class="screen-canvas"
                    :class="{ picking: interactionMode === 'pick' }"
                    @mousedown.prevent="onMouseDown"
                    @mouseup.prevent="onMouseUp"
                  />
                  <div
                    v-if="lastPickMarker && interactionMode === 'pick'"
                    class="pick-marker"
                    :style="pickMarkerStyle"
                  />
                  <div v-if="!hasFrame" class="screen-placeholder">
                    <el-icon :size="40"><Monitor /></el-icon>
                    <p>{{ statusText }}</p>
                    <p class="frame-dim">{{ resolutionLabel }}</p>
                    <el-button v-if="!connecting && !connected" type="primary" @click="connectStream">连接投屏</el-button>
                  </div>
                </div>
                <ScreenNavBar :disabled="!connected" @key="pressNavKey" />
              </div>
            </div>
          </AppCard>
        </aside>

        <section class="detail-panel">
          <AppCard title="拾取操作" :hover="false">
            <div class="pick-toolbar">
              <el-button type="primary" size="small" :disabled="connected" :loading="connecting" @click="connectStream">连接</el-button>
              <el-button size="small" :disabled="!connected" @click="stopStream">断开</el-button>
              <el-divider direction="vertical" />
              <el-button type="primary" size="small" :loading="warming" :disabled="!connected" @click="refreshUiTree">刷新 UI 树</el-button>
              <el-button v-if="currentPick?.needs_context_switch" size="small" type="warning" :disabled="!connected" @click="switchWebViewContext">手动切换 WebView</el-button>
              <el-tag v-if="pageContext && pageContext !== 'native'" size="small" type="warning" effect="plain">页面上下文：{{ pageContextLabel }}</el-tag>
              <el-button v-if="currentPick && !currentPick.valid" size="small" :disabled="!connected" @click="ocrFallbackPick">OCR 兜底</el-button>
              <el-tag v-if="uiTreeReady" type="success" size="small" effect="plain">已就绪</el-tag>
              <el-tag v-else-if="connected" type="info" size="small" effect="plain">未刷新</el-tag>
              <el-divider direction="vertical" />
              <el-radio-group v-model="interactionMode" size="small">
                <el-radio-button value="operate">操控设备</el-radio-button>
                <el-radio-button value="pick">识别控件</el-radio-button>
              </el-radio-group>
              <span class="resolution-tag">{{ resolutionLabel }}</span>
            </div>
            <p class="hint">
              <template v-if="interactionMode === 'operate'">单击画面操作设备（点击/滑动），不会识别控件。</template>
              <template v-else>先点「刷新 UI 树」，再在画面上单击要识别的控件（不会点击设备）。</template>
            </p>
          </AppCard>

          <AppCard title="控件定位结果" :hover="false" class="panel-block pick-result-card">
            <div v-if="inspecting" class="inspecting-tip">
              <el-icon class="spin"><Loading /></el-icon>
              识别中，请稍候…
            </div>
            <template v-else-if="currentPick">
              <div class="pick-summary">
                <div class="pick-title-row">
                  <h3 class="pick-title">{{ currentPick.display_name || currentPick.element_name || '未命名控件' }}</h3>
                  <div class="pick-badges">
                    <el-tag v-if="currentPick.valid && !isWeakPick(currentPick)" type="success" size="small" round>已识别</el-tag>
                    <el-tag v-else-if="isWeakPick(currentPick)" type="danger" size="small" round>高风险</el-tag>
                    <el-tag v-else type="warning" size="small" round>需补充</el-tag>
                    <el-tag v-if="currentPick.risk_level" :type="riskTagType(currentPick.risk_level)" size="small" round effect="light">
                      {{ riskLevelLabel(currentPick.risk_level) }}
                    </el-tag>
                    <el-tag v-for="tag in (currentPick.risk_tags || [])" :key="tag" size="small" type="warning" effect="plain" round>
                      {{ riskTagLabel(tag) }}
                    </el-tag>
                    <el-tag v-if="stabilityScore != null" :type="stabilityScoreType(stabilityScore)" size="small" round effect="dark">
                      稳定性 {{ stabilityScore }} · {{ stabilityScoreLabel(stabilityScore) }}
                    </el-tag>
                  </div>
                </div>

                <div class="basic-info-block">
                  <div class="basic-info-title">基础信息</div>
                  <el-descriptions :column="2" size="small" border class="pick-desc">
                    <el-descriptions-item label="控件名称">{{ currentPick.display_name || currentPick.element_name || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="控件类型">{{ widgetTypeLabel(currentPick.widget_type || currentPick.class) }}</el-descriptions-item>
                    <el-descriptions-item label="点击坐标">{{ currentPick.x }}, {{ currentPick.y }}</el-descriptions-item>
                    <el-descriptions-item label="界面坐标" v-if="currentPick.inspect_x != null">
                      {{ currentPick.inspect_x }}, {{ currentPick.inspect_y }}
                    </el-descriptions-item>
                    <el-descriptions-item label="类名" v-if="currentPick.class">{{ shortClass(currentPick.class) }}</el-descriptions-item>
                    <el-descriptions-item label="控件边界" :span="2" v-if="currentPick.bounds">{{ currentPick.bounds }}</el-descriptions-item>
                  </el-descriptions>
                </div>

                <el-alert
                  v-if="currentPick.risk_reasons?.length"
                  :title="currentPick.risk_reasons[0]"
                  :description="currentPick.risk_reasons.length > 1 ? currentPick.risk_reasons.slice(1).join('；') : undefined"
                  type="warning"
                  :closable="false"
                  show-icon
                  class="pick-alert"
                />

                <el-alert
                  v-if="currentPick.auto_context_switched && currentPick.context_hint"
                  :title="currentPick.context_hint"
                  type="success"
                  :closable="false"
                  show-icon
                  class="pick-alert"
                />
                <el-alert
                  v-else-if="currentPick.needs_context_switch"
                  title="检测到 WebView 混合页面，拾取时已尝试自动切换；仍异常可手动切换"
                  type="warning"
                  :closable="false"
                  show-icon
                  class="pick-alert"
                />

                <div class="locator-schemes">
                  <div class="basic-info-title">多套定位方案</div>
                  <div
                    v-for="scheme in locatorSchemes"
                    :key="scheme.key"
                    class="locator-scheme-card"
                    :class="{ recommend: scheme.recommended }"
                  >
                    <div class="scheme-head">
                      <span>{{ scheme.label }}<em v-if="scheme.recommended">（推荐优先使用）</em></span>
                      <el-button size="small" type="primary" plain :disabled="!scheme.value" @click="copyText(scheme.value)">一键复制</el-button>
                    </div>
                    <code class="scheme-value">{{ scheme.value || '暂无该定位方案' }}</code>
                  </div>
                </div>

                <div v-if="recommendedLocatorItem" class="primary-loc-card recommended">
                  <div class="plc-head">
                    <span class="plc-badge rec">推荐定位</span>
                    <span class="plc-type">{{ formatLocatorType(recommendedLocatorItem.type === 'resource_id' ? 'id' : recommendedLocatorItem.type) }}</span>
                    <el-tag v-if="!isSelectedDifferentFromRecommended" size="small" type="primary" effect="plain" round>当前选用</el-tag>
                  </div>
                  <p v-if="recommendReasonText" class="plc-reason">{{ recommendReasonText }}</p>
                  <div class="plc-value-row">
                    <code class="plc-value">{{ recommendedLocatorItem.value }}</code>
                    <el-button text type="primary" size="small" :icon="CopyDocument" @click="copyText(recommendedLocatorItem.value)" />
                  </div>
                </div>

                <div v-if="isSelectedDifferentFromRecommended && primaryLocatorItem" class="primary-loc-card selected">
                  <div class="plc-head">
                    <span class="plc-badge">当前选用</span>
                    <span class="plc-type">{{ formatLocatorType(primaryLocatorItem.type === 'resource_id' ? 'id' : primaryLocatorItem.type) }}</span>
                  </div>
                  <div class="plc-value-row">
                    <code class="plc-value">{{ primaryLocatorItem.value }}</code>
                    <el-button text type="primary" size="small" :icon="CopyDocument" @click="copyText(primaryLocatorItem.value)" />
                  </div>
                </div>

                <div class="relative-section">
                  <div class="section-head">
                    <span class="section-title">相对定位</span>
                    <span class="section-sub">配置后可加入定位链</span>
                  </div>
                  <div class="relative-grid">
                    <div class="relative-card">
                      <div class="rc-title">父容器 + 下标</div>
                      <el-input v-model="relativeForm.container" size="small" placeholder="容器 resource-id 短名" />
                      <el-input-number v-model="relativeForm.index" size="small" :min="0" :max="99" controls-position="right" />
                      <code v-if="relativePreview.parent_index" class="rc-preview">{{ relativePreview.parent_index }}</code>
                      <el-button size="small" text type="primary" :disabled="!relativePreview.parent_index" @click="addRelativeToChain('parent_index')">加入链</el-button>
                    </div>
                    <div class="relative-card">
                      <div class="rc-title">锚点邻位</div>
                      <el-input v-model="relativeForm.anchor" size="small" placeholder="锚点文本/content-desc" />
                      <el-select v-model="relativeForm.direction" size="small" style="width:100%">
                        <el-option v-for="d in ANCHOR_DIRECTIONS" :key="d.value" :label="d.label" :value="d.value" />
                      </el-select>
                      <code v-if="relativePreview.anchor_adjacent" class="rc-preview">{{ relativePreview.anchor_adjacent }}</code>
                      <el-button size="small" text type="primary" :disabled="!relativePreview.anchor_adjacent" @click="addRelativeToChain('anchor_adjacent')">加入链</el-button>
                    </div>
                    <div class="relative-card">
                      <div class="rc-title">区域限定</div>
                      <el-input v-model="relativeForm.region_bounds" size="small" placeholder="[x1,y1][x2,y2]" />
                      <div class="region-inner">
                        <el-select v-model="relativeForm.inner_type" size="small">
                          <el-option label="Content-Desc" value="content_desc" />
                          <el-option label="ID" value="id" />
                          <el-option label="文本" value="text" />
                        </el-select>
                        <el-input v-model="relativeForm.inner_value" size="small" placeholder="区域内匹配值" />
                      </div>
                      <code v-if="relativePreview.region_locator" class="rc-preview">{{ relativePreview.region_locator }}</code>
                      <el-button size="small" text type="primary" :disabled="!relativePreview.region_locator" @click="addRelativeToChain('region_locator')">加入链</el-button>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="locatorChain.length" class="loc-chain-section">
                <div class="section-head">
                  <span class="section-title">定位链</span>
                  <span class="section-sub">{{ enabledChainCount }}/{{ locatorChain.length }} 条启用 · 点击行切换主定位</span>
                </div>
                <div class="loc-list">
                  <div
                    v-for="(item, idx) in locatorChain"
                    :key="item.type + item.value + idx"
                    class="loc-item"
                    :class="{ primary: item.primary, recommended: item.recommended, off: item.enabled === false }"
                    @click="setChainPrimary(idx)"
                  >
                    <div class="loc-rank" :class="{ rec: item.recommended }">{{ idx + 1 }}</div>
                    <div class="loc-main">
                      <div class="loc-meta">
                        <span class="loc-type">{{ formatLocatorType(item.type === 'resource_id' ? 'id' : item.type) }}</span>
                        <el-tag v-if="item.recommended" size="small" type="success" effect="light" round>推荐</el-tag>
                        <el-tag v-if="item.primary" size="small" type="primary" effect="dark" round>主</el-tag>
                      </div>
                      <code class="loc-value" :class="{ clamp: !isLocatorExpanded(idx) && item.value.length > 72 }">
                        {{ item.value }}
                      </code>
                      <button
                        v-if="item.value.length > 72"
                        type="button"
                        class="loc-expand"
                        @click.stop="toggleLocatorExpand(idx)"
                      >
                        {{ isLocatorExpanded(idx) ? '收起' : '展开' }}
                      </button>
                    </div>
                    <div class="loc-side" @click.stop>
                      <el-switch v-model="item.enabled" size="small" @change="syncChainToPick" />
                      <el-button-group class="loc-order">
                        <el-button size="small" text :icon="ArrowUp" :disabled="idx === 0" @click="moveChainItem(idx, -1)" />
                        <el-button size="small" text :icon="ArrowDown" :disabled="idx >= locatorChain.length - 1" @click="moveChainItem(idx, 1)" />
                      </el-button-group>
                      <el-button size="small" text :icon="CopyDocument" @click="copyText(item.value)" />
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="currentPick?.validate_result" class="validate-meta">
                上次校验：{{ formatValidateMeta(currentPick.validate_result) }}
              </div>
              <div v-if="validateResult" class="validate-banner" :class="validateResult.valid ? 'ok' : 'fail'">
                <el-icon><CircleCheck v-if="validateResult.valid" /><WarningFilled v-else /></el-icon>
                <span>{{ validateResultText }}</span>
                <el-button v-if="validateAttempts.length" text size="small" @click="showValidateDetail = !showValidateDetail">
                  {{ showValidateDetail ? '收起' : '明细' }}
                </el-button>
              </div>
              <div v-if="showValidateDetail && validateAttempts.length" class="validate-detail">
                <div v-for="att in validateAttempts" :key="att.type + att.value" class="validate-line">
                  <span class="dot" :class="{ ok: att.clickable, warn: att.found && !att.clickable }" />
                  <span class="vt">{{ formatLocatorType(att.type) }}</span>
                  <span class="validate-status">{{ validateAttemptLabel(att) }}</span>
                  <code>{{ truncateLocator(att.value) }}</code>
                </div>
              </div>

              <div class="pick-actions-bar">
                <el-button size="small" type="primary" @click="openSavePool">保存至公共控件库</el-button>
                <el-button size="small" @click="copyAllLocators">复制全部定位信息</el-button>
                <el-button size="small" @click="clearCurrentPick">清空当前拾取数据</el-button>
                <el-button size="small" :loading="validating" :disabled="!connected || !locatorChain.length" @click="validateCurrentLocator">
                  验证定位
                </el-button>
                <el-button
                  v-if="pickRecordId != null && pickStepIndex != null"
                  size="small"
                  type="success"
                  :loading="applying"
                  @click="applyToReviewStep"
                >
                  应用到审阅
                </el-button>
              </div>
            </template>
            <el-empty v-else description="切换到「识别控件」并单击画面拾取" :image-size="64" />
          </AppCard>

          <AppCard title="手动填写" :hover="false" class="panel-block">
            <el-form label-width="88px" size="small">
              <el-form-item label="显示名称">
                <el-input v-model="manualForm.display_name" placeholder="如：登录按钮" />
              </el-form-item>
              <el-form-item label="元素名">
                <el-input v-model="manualForm.element_name" placeholder="脚本变量名" />
              </el-form-item>
              <el-form-item label="定位方式">
                <el-select v-model="manualForm.locator_type" style="width:100%">
                  <el-option label="Resource ID" value="id" />
                  <el-option label="文本" value="text" />
                  <el-option label="Content-Desc" value="content_desc" />
                  <el-option label="XPath" value="xpath" />
                  <el-option label="OCR 文本" value="ocr" />
                </el-select>
              </el-form-item>
              <el-form-item label="定位值">
                <el-input v-model="manualForm.locator_value" placeholder="Resource ID / 文本 / XPath" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :disabled="!manualForm.locator_value" @click="applyManualToCurrent">应用到当前</el-button>
              </el-form-item>
            </el-form>
          </AppCard>

          <AppCard title="本次拾取历史控件列表" :hover="false" class="panel-block">
            <el-scrollbar max-height="260px">
              <div
                v-for="(item, idx) in pickHistory"
                :key="item.id"
                class="history-row"
                :class="{ active: currentPick?.id === item.id }"
                @click="selectHistory(item)"
              >
                <span class="hist-no">{{ pickHistory.length - idx }}</span>
                <div class="hist-body">
                  <span class="hist-name">{{ item.display_name || item.element_name || `控件 (${item.x},${item.y})` }}</span>
                  <span class="hist-meta">所属设备：{{ item.device_label || device?.model || device?.name || '-' }}</span>
                  <span class="hist-meta">拾取时间：{{ formatPickTime(item.picked_at) }}</span>
                  <span v-if="formatStepLocator(item)" class="hist-loc">{{ formatStepLocator(item) }}</span>
                </div>
              </div>
            </el-scrollbar>
            <div class="history-footer">
              <el-button v-if="pickHistory.length" size="small" link type="danger" @click="clearPickHistory">清空历史</el-button>
              <el-button type="primary" link @click="$router.push('/controls')">查看全部公共控件库</el-button>
            </div>
          </AppCard>
        </section>
      </div>

      <el-dialog v-model="showSavePool" title="保存至公共控件库" width="480px" destroy-on-close>
        <el-form label-width="96px" size="small">
          <el-form-item label="控件名称" required>
            <el-input v-model="savePoolForm.element_name" placeholder="如：登录按钮" />
          </el-form-item>
          <el-form-item label="业务分类">
            <el-input v-model="savePoolForm.page_name" placeholder="如：登录页 / 首页" />
          </el-form-item>
          <el-form-item label="版本标签">
            <el-input v-model="savePoolForm.version_tag" placeholder="v1.0.0 / test" />
          </el-form-item>
          <el-form-item label="环境标签">
            <el-input v-model="savePoolForm.env_tag" placeholder="test / staging" />
          </el-form-item>
          <el-form-item label="控件分级">
            <el-select v-model="savePoolForm.control_tag" style="width:100%">
              <el-option v-for="t in CONTROL_TAGS" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="等待规则">
            <div class="wait-rule-row">
              <el-select v-model="savePoolForm.wait_condition" size="small" style="width:110px">
                <el-option v-for="c in WAIT_CONDITIONS" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
              <el-input-number v-model="savePoolForm.wait_timeout_ms" size="small" :min="0" :max="60000" :step="500" />
              <span class="hint">ms（0=不等待）</span>
            </div>
          </el-form-item>
          <el-form-item label="主定位">
            <code class="save-loc-preview">{{ savePoolPreview }}</code>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showSavePool = false">取消</el-button>
          <el-button type="primary" :loading="savingPool" @click="saveToControlPool">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, onActivated, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deviceApi, recordApi, controlApi } from '@/api'
import { useScreenStream, setScreenDeviceInfo } from '@/composables/useScreenStream'
import { createScreenCanvasRenderer } from '@/composables/useScreenCanvas'
import { fixedScreenFrameStyle, frameSizeFromDevice, frameMaxHeight, NAV_BAR_HEIGHT } from '@/composables/screenFrameStyle'
import { formatStepLocator, formatLocatorType } from '@/utils/stepDisplay'
import {
  buildLocatorChainFromPick, chainToLocators, primaryFromChain, isWeakPick,
  riskLevelLabel, riskTagLabel, riskTagType, mapLocatorTypeForPool,
  ANCHOR_DIRECTIONS, parseLocatorKv,
  buildParentIndexValue, buildAnchorAdjacentValue, buildRegionLocatorValue,
  computeStabilityScore, stabilityScoreLabel, stabilityScoreType,
  CONTROL_TAGS, WAIT_CONDITIONS, validateElementName, recommendReasonLabel
} from '@/utils/locatorAssist'
import { normalizeDevice } from '@/utils/device'
import { useUserStore } from '@/stores/user'
import ScreenNavBar from '@/components/ScreenNavBar.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Loading, CopyDocument, ArrowUp, ArrowDown, CircleCheck, WarningFilled } from '@element-plus/icons-vue'

defineOptions({ name: 'ElementPicker' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const deviceId = computed(() => route.params.id ? String(route.params.id) : '')
const device = ref(null)
const canvasRef = ref(null)
const screenWrapRef = ref(null)
const hasFrame = ref(false)
const frameW = ref(1080)
const frameH = ref(1920)

const devicesLoading = ref(false)
const androidDevices = ref([])
const rebootingOffline = ref(false)
const syncingUsb = ref(false)
const showWhitelistDialog = ref(false)
const whitelistForm = reactive({ serial_number: '', platform: 'android', remark: '' })

const interactionMode = ref('pick')
const inspecting = ref(false)
const warming = ref(false)
const uiTreeReady = ref(false)
const pageContext = ref('native')
const applying = ref(false)
const validating = ref(false)
const savingPool = ref(false)
const currentPick = ref(null)
const PICK_HISTORY_KEY = 'atp_element_pick_history'

function loadPersistedPickHistory() {
  try {
    const raw = JSON.parse(localStorage.getItem(PICK_HISTORY_KEY) || '[]')
    return Array.isArray(raw) ? raw.slice(0, 30) : []
  } catch {
    return []
  }
}

function persistPickHistory(list) {
  try {
    localStorage.setItem(PICK_HISTORY_KEY, JSON.stringify((list || []).slice(0, 30)))
  } catch { /* ignore */ }
}

const pickHistory = ref(loadPersistedPickHistory())
const lastPickMarker = ref(null)
const locatorChain = ref([])
const validateResult = ref(null)
const validateAttempts = ref([])
const showValidateDetail = ref(false)
const expandedLocators = ref(new Set())
const showSavePool = ref(false)
let pickSeq = 0

const savePoolForm = reactive({
  page_name: '',
  element_name: '',
  version_tag: '',
  env_tag: '',
  control_tag: 'static',
  wait_condition: 'clickable',
  wait_timeout_ms: 0
})
const pickContextVersion = ref('')
const pickContextEnv = ref('')

const relativeForm = reactive({
  container: '',
  index: 0,
  anchor: '',
  direction: 'right',
  region_bounds: '',
  inner_type: 'content_desc',
  inner_value: ''
})

const pickRecordId = ref(null)
const pickStepIndex = ref(null)
const pickReturnTo = ref('')
const pickReviewHint = computed(() => pickRecordId.value != null && pickStepIndex.value != null)

const manualForm = reactive({
  display_name: '',
  element_name: '',
  locator_type: 'id',
  locator_value: ''
})

let dragStart = null
let dragStartClient = null
const SWIPE_THRESHOLD = 12

const {
  connected, connecting, statusText, nativeW, nativeH, streamW, streamH,
  fps, latencyMs, lastFrame, startStream, stopStream, resumeIfAlive,
  attachFrameListener, attachMetaListener
} = useScreenStream(deviceId)

let renderer = null
let detachFrame = null
let detachMeta = null

const layoutStyle = ref(fixedScreenFrameStyle(1080, 1920, frameMaxHeight('calc(100vh - 220px)', NAV_BAR_HEIGHT)))
const screenFrameStyle = computed(() => layoutStyle.value)

const deviceCoordW = computed(() => device.value?.screen_width || frameW.value || 1080)
const deviceCoordH = computed(() => device.value?.screen_height || frameH.value || 1920)
const resolutionLabel = computed(() => `${deviceCoordW.value} × ${deviceCoordH.value}`)

const pickMarkerStyle = computed(() => {
  if (!lastPickMarker.value) return {}
  const dw = deviceCoordW.value || 1
  const dh = deviceCoordH.value || 1
  return {
    left: `${(lastPickMarker.value.x / dw) * 100}%`,
    top: `${(lastPickMarker.value.y / dh) * 100}%`
  }
})

const savePoolPreview = computed(() => {
  const p = primaryFromChain(locatorChain.value)
  if (!p.locator_value) return '-'
  return `${formatLocatorType(p.locator_type)} · ${p.locator_value}`
})

const primaryLocatorItem = computed(() => {
  return locatorChain.value.find(i => i.primary && i.enabled !== false)
    || locatorChain.value.find(i => i.enabled !== false)
    || null
})

const recommendedLocatorItem = computed(() => {
  return locatorChain.value.find(i => i.recommended)
    || locatorChain.value[0]
    || null
})

const isSelectedDifferentFromRecommended = computed(() => {
  const rec = recommendedLocatorItem.value
  const sel = primaryLocatorItem.value
  if (!rec || !sel) return false
  return rec.type !== sel.type || rec.value !== sel.value
})

const enabledChainCount = computed(() => locatorChain.value.filter(i => i.enabled !== false).length)

const relativePreview = computed(() => ({
  parent_index: buildParentIndexValue(relativeForm.container, relativeForm.index),
  anchor_adjacent: buildAnchorAdjacentValue(relativeForm.anchor, relativeForm.direction),
  region_locator: buildRegionLocatorValue(relativeForm.region_bounds, relativeForm.inner_type, relativeForm.inner_value)
}))

const stabilityScore = computed(() => computeStabilityScore(currentPick.value))

const pageContextLabel = computed(() => {
  const map = { native: '原生', webview: 'WebView', hybrid: '混合' }
  return map[pageContext.value] || pageContext.value
})

const recommendReasonText = computed(() => {
  const pick = currentPick.value
  if (pick?.recommend_reason) return pick.recommend_reason
  const rec = recommendedLocatorItem.value
  if (!rec) return ''
  return rec.recommend_reason || recommendReasonLabel(rec.type)
})

const locatorSchemes = computed(() => {
  const pick = currentPick.value
  const locs = pick?.locators || {}
  const chain = locatorChain.value || []
  const findVal = (...keys) => {
    for (const k of keys) {
      if (locs[k]) return String(locs[k])
      const hit = chain.find(i => i.type === k || (k === 'id' && i.type === 'resource_id'))
      if (hit?.value) return String(hit.value)
    }
    if (pick?.locator_type && keys.includes(pick.locator_type) && pick.locator_value) return String(pick.locator_value)
    return ''
  }
  const idVal = findVal('id', 'resource_id')
  const textVal = findVal('text')
  const descVal = findVal('content_desc')
  const xpathVal = findVal('xpath', 'xpath_desc', 'absolute_xpath', 'relative_xpath')
  const recType = recommendedLocatorItem.value?.type
  return [
    { key: 'id', label: '资源 ID 定位', value: idVal, recommended: recType === 'id' || recType === 'resource_id' || (!recType && !!idVal) },
    { key: 'text', label: '文本内容定位', value: textVal, recommended: recType === 'text' },
    { key: 'content_desc', label: '内容描述定位', value: descVal, recommended: recType === 'content_desc' },
    { key: 'xpath', label: '路径通用定位', value: xpathVal, recommended: ['xpath', 'xpath_desc', 'absolute_xpath', 'relative_xpath'].includes(recType) }
  ]
})

const onlineDeviceCount = computed(() => androidDevices.value.filter(d => isDeviceOnline(d)).length)
const offlineDeviceCount = computed(() => androidDevices.value.filter(d => !isDeviceOnline(d)).length)
const hubBanner = computed(() => {
  const total = androidDevices.value.length
  const online = onlineDeviceCount.value
  const offline = offlineDeviceCount.value
  if (!total || online === 0) {
    return { tone: 'danger', icon: 'WarningFilled', text: '当前无可用在线设备，离线设备无法启动控件拾取' }
  }
  if (offline === 0) {
    return { tone: 'success', icon: 'CircleCheck', text: `当前共 ${total} 台设备，其中 ${online} 台在线，可正常拾取控件` }
  }
  return { tone: 'warning', icon: 'WarningFilled', text: `${online} 台设备在线可用，${offline} 台设备离线（离线设备拾取功能已禁用）` }
})

function isDeviceOnline(row) {
  return row?.status === 'online' || row?.status === 'busy'
}

function canStartPick(row) {
  return (row.platform === 'android' || row.platform === 'ios') && isDeviceOnline(row)
}

function widgetTypeLabel(raw) {
  const s = String(raw || '').toLowerCase()
  if (!s || s === 'unknown') return '未知类型'
  if (s.includes('button') || s.includes('btn')) return '按钮'
  if (s.includes('edit') || s.includes('input') || s.includes('textfield')) return '文本框'
  if (s.includes('image') || s.includes('img') || s.includes('imageview')) return '图片'
  if (s.includes('text') || s.includes('label') || s.includes('textview')) return '文本'
  if (s.includes('check')) return '复选框'
  if (s.includes('switch')) return '开关'
  if (s.includes('list') || s.includes('recycler')) return '列表'
  if (s.includes('web')) return '网页视图'
  return raw || '未知类型'
}

function formatPickTime(ts) {
  if (!ts) return '-'
  try {
    const d = new Date(ts)
    if (Number.isNaN(d.getTime())) return String(ts)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  } catch {
    return String(ts)
  }
}

const validateResultText = computed(() => {
  const r = validateResult.value
  if (!r) return ''
  if (r.valid) return `校验通过 · 可点击 · ${formatLocatorType(r.matched_by)}`
  if (r.error === 'not_clickable') return `存在但不可点击 · ${formatLocatorType(r.matched_by || '')}`
  return r.error || '校验未通过'
})

function validateAttemptLabel(att) {
  if (att.clickable) return '可点击'
  if (att.found && att.visible) return '不可点'
  if (att.found) return '不可见'
  return att.reason === 'not_found' ? '未命中' : (att.reason || '未命中')
}

function hasDuplicateRisk(pick) {
  return (pick?.risk_tags || []).includes('duplicate')
}

async function confirmDuplicateIfNeeded(actionLabel = '继续') {
  if (!hasDuplicateRisk(currentPick.value)) return true
  try {
    await ElMessageBox.confirm(
      '当前定位在当前页面匹配多处，可能误点。请确认主定位唯一，或在定位链中调整策略后再操作。',
      '重复匹配预警',
      { type: 'warning', confirmButtonText: actionLabel, cancelButtonText: '取消' }
    )
    return true
  } catch {
    return false
  }
}

function shortClass(clazz) {
  return String(clazz || '').split('.').pop() || clazz
}

function isLocatorExpanded(idx) {
  return expandedLocators.value.has(idx)
}

function toggleLocatorExpand(idx) {
  const next = new Set(expandedLocators.value)
  if (next.has(idx)) next.delete(idx)
  else next.add(idx)
  expandedLocators.value = next
}

function truncateLocator(val, max = 48) {
  const s = String(val || '')
  return s.length <= max ? s : `${s.slice(0, max - 3)}...`
}

function syncRelativeFormFromPick(pick) {
  const locs = pick?.locators || {}
  const pi = parseLocatorKv(locs.parent_index || '')
  const aa = parseLocatorKv(locs.anchor_adjacent || '')
  const rl = parseLocatorKv(locs.region_locator || '')
  relativeForm.container = pi.container || ''
  relativeForm.index = Number.isFinite(Number(pi.index)) ? Number(pi.index) : 0
  relativeForm.anchor = aa.anchor || ''
  relativeForm.direction = aa.dir || 'right'
  relativeForm.region_bounds = rl.region || pick?.bounds || ''
  relativeForm.inner_type = rl.type || 'content_desc'
  relativeForm.inner_value = rl.value || ''
}

function formatValidateMeta(vr) {
  if (!vr) return '-'
  const at = vr.validated_at ? new Date(vr.validated_at).toLocaleString() : '未知时间'
  const status = vr.valid ? '通过' : (vr.error === 'not_clickable' ? '不可点' : '未通过')
  const by = vr.matched_by ? ` · ${formatLocatorType(vr.matched_by)}` : ''
  return `${at} · ${status}${by}`
}

function addRelativeToChain(type) {
  const value = relativePreview.value[type]
  if (!value) return
  if (locatorChain.value.some(i => i.type === type && i.value === value)) {
    ElMessage.info('该相对定位已在链中')
    return
  }
  const isFirst = !locatorChain.value.length
  locatorChain.value = [
    ...locatorChain.value,
    {
      type,
      value,
      enabled: true,
      priority: locatorChain.value.length + 1,
      recommended: isFirst,
      primary: isFirst
    }
  ]
  syncChainToPick()
  ElMessage.success('已加入定位链')
}

function syncChainToPick() {
  if (!currentPick.value) return
  const locators = chainToLocators(locatorChain.value)
  const primary = primaryFromChain(locatorChain.value)
  currentPick.value = {
    ...currentPick.value,
    locators,
    locator_chain: locatorChain.value.map((item, idx) => ({ ...item, priority: idx + 1 })),
    locator_type: primary.locator_type,
    locator_value: primary.locator_value
  }
  fillManualForm(currentPick.value)
}

function updateLayoutSize() {
  const { w, h } = frameSizeFromDevice(device.value || { screen_width: frameW.value, screen_height: frameH.value })
  layoutStyle.value = fixedScreenFrameStyle(
    w,
    h,
    frameMaxHeight('calc(100vh - 220px)', NAV_BAR_HEIGHT),
    Math.min(window.innerWidth * 0.38, 420)
  )
}

function redrawLastFrame() {
  nextTick(() => {
    if (renderer && lastFrame.value) {
      renderer.onFrame(lastFrame.value)
    }
  })
}

function moveChainItem(index, delta) {
  const next = index + delta
  if (next < 0 || next >= locatorChain.value.length) return
  const arr = [...locatorChain.value]
  const tmp = arr[index]
  arr[index] = arr[next]
  arr[next] = tmp
  locatorChain.value = arr.map((item, idx) => ({ ...item, priority: idx + 1 }))
  syncChainToPick()
}

function setChainPrimary(index) {
  if (index < 0 || index >= locatorChain.value.length) return
  if (locatorChain.value[index].enabled === false) return
  locatorChain.value = locatorChain.value.map((item, idx) => ({
    ...item,
    primary: idx === index
  }))
  syncChainToPick()
}

function resolveAppPackage() {
  return device.value?.app_package?.trim()
    || device.value?.current_package?.trim()
    || 'com.atp.unscoped'
}

function maybeFillControlForm(pick) {
  if (route.query.return_fill !== '1' && sessionStorage.getItem('atp_fill_control_form') !== '1') return
  const primary = (pick.locator_type && pick.locator_value)
    ? { type: pick.locator_type, value: pick.locator_value }
    : (pick.locators && Object.keys(pick.locators).length
        ? { type: Object.keys(pick.locators)[0], value: pick.locators[Object.keys(pick.locators)[0]] }
        : null)
  const payload = {
    app_package: device.value?.app_package || pick.app_package || '',
    page_name: pick.page_name || '',
    element_name: pick.element_name || pick.text || pick.content_desc || '',
    platform: (device.value?.platform || 'android').toLowerCase().includes('ios') ? 'ios' : 'android',
    locator_type: primary?.type || 'id',
    locator_value: primary?.value || '',
    version_tag: '',
    env_tag: '',
    control_tag: 'static',
    is_core: false
  }
  if (payload.element_name && !/[\u4e00-\u9fff]/.test(payload.element_name)) {
    const zh = pick.text || pick.content_desc || pick.display_name
    if (zh && /[\u4e00-\u9fff]/.test(zh)) payload.element_name = zh
  }
  sessionStorage.setItem('atp_control_pool_form_fill', JSON.stringify(payload))
  localStorage.setItem('atp_control_pool_form_fill', JSON.stringify(payload))
  sessionStorage.removeItem('atp_fill_control_form')
  ElMessage.success('定位信息已回填，正在返回控件池表单')
  router.push({ path: '/controls', query: { open_form: '1' } })
}

function goHub() {
  router.push('/element-picker')
}

function goReturn() {
  if (pickReturnTo.value) router.push(pickReturnTo.value)
}

function openPicker(id) {
  router.push(`/element-picker/${id}`)
}

async function loadDevices() {
  devicesLoading.value = true
  try {
    const res = await deviceApi.list({ page: 1, page_size: 100 })
    androidDevices.value = (res.data?.list || [])
      .filter(d => d.platform === 'android' || d.platform === 'ios')
      .map(normalizeDevice)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '加载设备列表失败'))
  } finally {
    devicesLoading.value = false
  }
}

async function syncUsbFromHub() {
  syncingUsb.value = true
  try {
    const res = await deviceApi.syncUsb()
    ElMessage.success(res.data?.message || 'USB 设备已同步')
    await loadDevices()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '同步失败'))
  } finally {
    syncingUsb.value = false
  }
}

async function rebootAllOffline() {
  const offline = androidDevices.value.filter(d => !isDeviceOnline(d))
  if (!offline.length) {
    ElMessage.info('当前没有离线设备')
    return
  }
  rebootingOffline.value = true
  try {
    let ok = 0
    for (const d of offline) {
      try {
        if (typeof deviceApi.resetHealth === 'function') await deviceApi.resetHealth(d.id)
        else await deviceApi.updateStatus(d.id, { status: 'online' })
        ok++
      } catch { /* continue */ }
    }
    ElMessage.success(`已向 ${ok}/${offline.length} 台离线设备下发重启指令`)
    await loadDevices()
  } finally {
    rebootingOffline.value = false
  }
}

async function addWhitelistFromHub() {
  if (!whitelistForm.serial_number?.trim()) {
    ElMessage.warning('请填写序列号')
    return
  }
  await deviceApi.addWhitelist(whitelistForm)
  ElMessage.success('白名单添加成功')
  showWhitelistDialog.value = false
  Object.assign(whitelistForm, { serial_number: '', platform: 'android', remark: '' })
  await loadDevices()
}

function endPickSession() {
  try { stopStream() } catch { /* ignore */ }
  currentPick.value = null
  lastPickMarker.value = null
  goHub()
}

function captureScreenshot() {
  const canvas = canvasRef.value
  if (!canvas) {
    ElMessage.warning('暂无投屏画面可截取')
    return
  }
  try {
    const url = canvas.toDataURL('image/png')
    const a = document.createElement('a')
    a.href = url
    a.download = `picker-${deviceId.value || 'screen'}-${Date.now()}.png`
    a.click()
    ElMessage.success('截图已下载')
  } catch (e) {
    ElMessage.error('截图失败')
  }
}

function clearCurrentPick() {
  currentPick.value = null
  lastPickMarker.value = null
  locatorChain.value = []
  validateResult.value = null
  validateAttempts.value = []
  ElMessage.success('已清空当前拾取数据')
}

function copyAllLocators() {
  if (!currentPick.value) return
  const lines = locatorSchemes.value
    .filter(s => s.value)
    .map(s => `${s.label}: ${s.value}`)
  if (!lines.length) {
    copyJson()
    return
  }
  copyText(lines.join('\n'))
}

function apiErrorMessage(e, fallback = '请求失败') {
  return e?.response?.data?.message
    || e?.response?.data?.error?.message
    || (e?.message && !String(e.message).includes('status code') ? e.message : fallback)
}

function initReviewContext() {
  const q = route.query
  if (q.pickRecord) pickRecordId.value = Number(q.pickRecord)
  if (q.pickStep != null && q.pickStep !== '') pickStepIndex.value = Number(q.pickStep)
  if (typeof q.returnTo === 'string') pickReturnTo.value = q.returnTo
  pickContextVersion.value = typeof q.versionTag === 'string' ? q.versionTag : ''
  pickContextEnv.value = typeof q.envTag === 'string' ? q.envTag : ''
}

async function loadDevice() {
  if (!deviceId.value) return
  const res = await deviceApi.get(deviceId.value)
  device.value = normalizeDevice(res.data)
  const size = frameSizeFromDevice(device.value)
  frameW.value = size.w
  frameH.value = size.h
  updateLayoutSize()
  setScreenDeviceInfo(deviceId.value, {
    name: device.value?.name || device.value?.serial_number
  })
}

function bindCanvasPipeline() {
  if (!canvasRef.value) return
  if (renderer) renderer.destroy()
  renderer = createScreenCanvasRenderer(canvasRef, {
    onFrameDrawn: () => {
      hasFrame.value = true
    }
  })
  if (detachFrame) detachFrame()
  if (detachMeta) detachMeta()
  detachFrame = attachFrameListener(buffer => {
    if (!renderer) return
    renderer.onFrame(buffer)
  })
  detachMeta = attachMetaListener(meta => {
    renderer?.onMeta(meta)
  })
}

function mapCoords(event) {
  const canvas = canvasRef.value
  if (!canvas || !canvas.width || !canvas.height) return { x: 0, y: 0 }
  const rect = canvas.getBoundingClientRect()
  const cx = (event.clientX - rect.left) * (canvas.width / rect.width)
  const cy = (event.clientY - rect.top) * (canvas.height / rect.height)
  const sx = Math.max(0, Math.min(cx, canvas.width))
  const sy = Math.max(0, Math.min(cy, canvas.height))
  const dw = deviceCoordW.value
  const dh = deviceCoordH.value
  return {
    x: Math.round(sx * dw / canvas.width),
    y: Math.round(sy * dh / canvas.height)
  }
}

async function refreshUiTree() {
  if (!connected.value) return
  warming.value = true
  uiTreeReady.value = false
  try {
    const res = await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
    uiTreeReady.value = true
    pageContext.value = res.data?.page_context || 'native'
    const ctxHint = pageContext.value !== 'native' ? `（${pageContextLabel.value}）` : ''
    ElMessage.success(`UI 树已刷新${ctxHint}，可点击画面识别控件`)
  } catch (e) {
    ElMessage.warning(apiErrorMessage(e, 'UI 树刷新失败'))
  } finally {
    warming.value = false
  }
}

async function inspectAt(x, y) {
  inspecting.value = true
  lastPickMarker.value = { x, y }
  try {
    if (!uiTreeReady.value) {
      warming.value = true
      try {
        await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
        uiTreeReady.value = true
      } finally {
        warming.value = false
      }
    }

    const dw = deviceCoordW.value
    const dh = deviceCoordH.value
    let res = await deviceApi.screenInspect(deviceId.value, {
      x, y, display_width: dw, display_height: dh, blocking: true
    })
    let data = { ...(res.data || {}), x, y, id: ++pickSeq }

    if (data.inspect_error === 'cache_miss' || data.inspect_error === 'ui_dump_failed') {
      await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
      uiTreeReady.value = true
      res = await deviceApi.screenInspect(deviceId.value, {
        x, y, display_width: dw, display_height: dh, blocking: true
      })
      data = { ...(res.data || {}), x, y, id: data.id }
    }

    if (isWeakPick(data)) {
      res = await deviceApi.screenInspect(deviceId.value, {
        x, y, display_width: dw, display_height: dh, blocking: true
      })
      data = { ...(res.data || {}), x, y, id: data.id }
    }

    applyPickResult(data)
    return data
  } catch (e) {
    const fallback = { x, y, valid: false, id: ++pickSeq }
    applyPickResult(fallback)
    ElMessage.warning(apiErrorMessage(e, '控件识别失败，请先刷新 UI 树'))
    return null
  } finally {
    inspecting.value = false
  }
}

function applyPickResult(data) {
  locatorChain.value = buildLocatorChainFromPick(data)
  const locators = chainToLocators(locatorChain.value)
  const primary = primaryFromChain(locatorChain.value)
  const merged = {
    ...data,
    locators,
    locator_chain: locatorChain.value.map((item, idx) => ({ ...item, priority: idx + 1 })),
    locator_type: primary.locator_type || data.locator_type,
    locator_value: primary.locator_value || data.locator_value,
    picked_at: Date.now(),
    device_label: device.value?.model || device.value?.name || device.value?.serial_number || `#${deviceId.value}`
  }
  fillManualForm(merged)
  currentPick.value = merged
  syncRelativeFormFromPick(merged)
  pickHistory.value.unshift(merged)
  if (pickHistory.value.length > 30) pickHistory.value.pop()
  persistPickHistory(pickHistory.value)
  maybeFillControlForm(merged)
  validateResult.value = null
  validateAttempts.value = []
  showValidateDetail.value = false
  expandedLocators.value = new Set()
  if (merged.auto_context_switched) {
    ElMessage.success(merged.context_hint || '已自动切换 WebView 上下文')
  } else if (merged.context) {
    pageContext.value = merged.context
  }
  if (!merged.valid || isWeakPick(merged)) {
    ElMessage.warning(isWeakPick(merged) ? '识别到高风险控件，请检查定位链或手动补充' : '未识别到稳定控件，可手动填写定位')
  }
  if (hasDuplicateRisk(merged)) {
    ElMessageBox.alert(
      '该定位在当前页面存在多处匹配，请从定位链中选择更唯一的主定位后再保存或应用。',
      '重复匹配预警',
      { type: 'warning' }
    ).catch(() => {})
  }
}

async function validateCurrentLocator() {
  if (!connected.value || !locatorChain.value.length) return
  validating.value = true
  validateResult.value = null
  validateAttempts.value = []
  showValidateDetail.value = false
  expandedLocators.value = new Set()
  try {
    const locators = chainToLocators(locatorChain.value)
    const res = await deviceApi.screenValidateLocator(deviceId.value, {
      locators,
      locator_chain: locatorChain.value.filter(i => i.enabled !== false)
    })
    validateResult.value = res.data || {}
    validateAttempts.value = res.data?.attempts || []
    const validatedAt = new Date().toISOString()
    const vr = {
      valid: !!res.data?.valid,
      matched_by: res.data?.matched_by || '',
      error: res.data?.error || '',
      attempts_count: validateAttempts.value.length,
      validated_at: validatedAt
    }
    if (currentPick.value) {
      currentPick.value = { ...currentPick.value, validate_result: vr, validated_at: validatedAt }
    }
    if (res.data?.valid) {
      ElMessage.success(`定位有效且可点击，命中 ${formatLocatorType(res.data.matched_by)}`)
    } else if (res.data?.error === 'not_clickable') {
      ElMessage.warning(`定位存在但不可点击（${formatLocatorType(res.data.matched_by)}），请换备用定位`)
    } else {
      ElMessage.warning('当前定位链未命中，请刷新 UI 树或调整备用定位')
    }
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '定位校验失败'))
  } finally {
    validating.value = false
  }
}

async function switchWebViewContext() {
  if (!deviceId.value || !connected.value) return
  try {
    await deviceApi.screenSwitchContext(deviceId.value, { target: 'webview' })
    await deviceApi.screenWarmUi(deviceId.value, { blocking: true })
    uiTreeReady.value = true
    ElMessage.success('已切换 WebView 上下文，请重新识别控件')
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '切换 WebView 失败'))
  }
}

async function ocrFallbackPick() {
  const pick = currentPick.value
  if (!pick || !connected.value) return
  try {
    const data = await inspectAt(pick.x, pick.y)
    if (data?.valid && (data.source === 'ocr_screen' || data.locators?.ocr)) {
      ElMessage.success('OCR 兜底识别成功')
    } else {
      ElMessage.warning('OCR 未识别到有效文本，请手动填写 OCR 定位')
    }
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, 'OCR 兜底失败'))
  }
}

function openSavePool() {
  if (!currentPick.value) return
  savePoolForm.page_name = ''
  savePoolForm.element_name = currentPick.value.element_name || manualForm.element_name || ''
  savePoolForm.version_tag = pickContextVersion.value || ''
  savePoolForm.env_tag = pickContextEnv.value || ''
  showSavePool.value = true
}

async function saveToControlPool() {
  if (!savePoolForm.element_name?.trim()) {
    ElMessage.warning('请填写元素名')
    return
  }
  const nameErr = validateElementName(savePoolForm.element_name.trim())
  if (nameErr) {
    ElMessage.warning(nameErr)
    return
  }
  if (!(await confirmDuplicateIfNeeded('仍要保存'))) return
  syncChainToPick()
  const pick = currentPick.value
  const primary = primaryFromChain(locatorChain.value)
  if (!primary.locator_value) {
    ElMessage.warning('请至少保留一条有效主定位')
    return
  }
  savingPool.value = true
  try {
    await controlApi.createPool({
      appPackage: resolveAppPackage(),
      pageName: savePoolForm.page_name?.trim() || '',
      elementName: savePoolForm.element_name.trim(),
      platform: device.value?.platform || 'android',
      locatorType: mapLocatorTypeForPool(primary.locator_type),
      locatorValue: primary.locator_value,
      versionTag: savePoolForm.version_tag?.trim() || '',
      envTag: savePoolForm.env_tag?.trim() || '',
      controlTag: savePoolForm.control_tag || 'static',
      waitRule: savePoolForm.wait_timeout_ms > 0 ? {
        condition: savePoolForm.wait_condition,
        timeout_ms: savePoolForm.wait_timeout_ms,
        interval_ms: 500
      } : undefined,
      displayName: pick.display_name || manualForm.display_name,
      widgetType: pick.widget_type,
      riskLevel: pick.risk_level,
      riskTags: pick.risk_tags || [],
      riskReasons: pick.risk_reasons || [],
      locators: pick.locators || {},
      locatorChain: locatorChain.value,
      validateResult: pick.validate_result || null,
      validatedAt: pick.validated_at || pick.validate_result?.validated_at || null,
      tapX: pick.x,
      tapY: pick.y
    })
    ElMessage.success('已保存到控件池')
    showSavePool.value = false
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '保存控件池失败'))
  } finally {
    savingPool.value = false
  }
}

function fillManualForm(data) {
  manualForm.display_name = data?.display_name || ''
  manualForm.element_name = data?.element_name || ''
  const primary = primaryFromChain(locatorChain.value.length ? locatorChain.value : buildLocatorChainFromPick(data || {}))
  manualForm.locator_type = primary.locator_type || data?.locator_type || 'id'
  manualForm.locator_value = primary.locator_value || data?.locator_value || ''
}

function buildPatchFromPick(pick) {
  syncChainToPick()
  const locators = chainToLocators(locatorChain.value)
  const primary = primaryFromChain(locatorChain.value)
  const patch = {
    x: pick.x,
    y: pick.y,
    display_name: pick.display_name || manualForm.display_name || undefined,
    element_name: pick.element_name || manualForm.element_name || undefined,
    locator_type: primary.locator_type || pick.locator_type,
    locator_value: primary.locator_value || pick.locator_value,
    locators,
    locator_chain: locatorChain.value,
    risk_level: pick.risk_level,
    risk_tags: pick.risk_tags,
    risk_reasons: pick.risk_reasons,
    widget_type: pick.widget_type,
    suggested_step_type: pick.suggested_step_type || 'click',
    locator_valid: !!pick.valid && !isWeakPick(pick)
  }
  if (pick.validate_result) {
    patch.validate_result = pick.validate_result
    patch.validated_at = pick.validated_at || pick.validate_result.validated_at
    patch.last_validation = pick.validate_result
  }
  if (manualForm.locator_value?.trim()) {
    const lt = manualForm.locator_type
    patch.locator_type = lt
    patch.locator_value = manualForm.locator_value.trim()
    patch.locators[lt] = patch.locator_value
    if (lt === 'ocr') patch.suggested_step_type = 'tap_ocr'
    patch.locator_valid = true
  }
  if (Object.keys(patch.locators).length) patch.locator_valid = true
  return patch
}

async function applyToReviewStep() {
  if (!currentPick.value || pickRecordId.value == null || pickStepIndex.value == null) return
  if (!(await confirmDuplicateIfNeeded('仍要应用'))) return
  applying.value = true
  try {
    const patch = buildPatchFromPick(currentPick.value)
    if (!patch.locator_valid) {
      ElMessage.warning('请先填写或识别至少一种有效定位方式')
      return
    }
    await recordApi.patchStepLocator(pickRecordId.value, pickStepIndex.value, patch)
    ElMessage.success('已应用到审阅步骤')
    goReturn()
  } catch (e) {
    ElMessage.error(e?.message || '应用失败')
  } finally {
    applying.value = false
  }
}

function applyManualToCurrent() {
  if (!manualForm.locator_value?.trim()) return
  const base = currentPick.value || { x: 0, y: 0, id: ++pickSeq, locators: {} }
  const lt = manualForm.locator_type
  const val = manualForm.locator_value.trim()
  const updated = {
    ...base,
    display_name: manualForm.display_name || base.display_name,
    element_name: manualForm.element_name || base.element_name,
    locator_type: lt,
    locator_value: val,
    locators: { ...(base.locators || {}), [lt]: val },
    valid: true,
    suggested_step_type: lt === 'ocr' ? 'tap_ocr' : 'click'
  }
  currentPick.value = updated
  const idx = pickHistory.value.findIndex(h => h.id === updated.id)
  if (idx >= 0) pickHistory.value[idx] = updated
  else pickHistory.value.unshift(updated)
  persistPickHistory(pickHistory.value)
  ElMessage.success('已更新当前控件')
}

function clearPickHistory() {
  pickHistory.value = []
  persistPickHistory([])
}

function selectHistory(item) {
  currentPick.value = item
  locatorChain.value = buildLocatorChainFromPick(item)
  fillManualForm(item)
  lastPickMarker.value = { x: item.x, y: item.y }
  validateResult.value = null
  validateAttempts.value = []
  showValidateDetail.value = false
  expandedLocators.value = new Set()
}

function copyText(text) {
  navigator.clipboard.writeText(String(text)).then(() => ElMessage.success('已复制')).catch(() => {})
}

function copyJson() {
  if (!currentPick.value) return
  const payload = buildPatchFromPick(currentPick.value)
  copyText(JSON.stringify(payload, null, 2))
}

function onMouseDown(event) {
  if (!connected.value) return
  dragStart = mapCoords(event)
  dragStartClient = { x: event.clientX, y: event.clientY }
}

async function onMouseUp(event) {
  if (!dragStart || !dragStartClient || !connected.value) return
  const start = dragStart
  const startClient = dragStartClient
  const end = mapCoords(event)
  const dx = Math.abs(event.clientX - startClient.x)
  const dy = Math.abs(event.clientY - startClient.y)
  dragStart = null
  dragStartClient = null

  if (dx > SWIPE_THRESHOLD || dy > SWIPE_THRESHOLD) {
    try {
      await deviceApi.screenSwipe(deviceId.value, {
        x1: start.x, y1: start.y, x2: end.x, y2: end.y, duration_ms: 300
      })
    } catch (e) {
      ElMessage.error(apiErrorMessage(e, '滑动失败'))
    }
    return
  }

  if (interactionMode.value === 'pick') {
    await inspectAt(end.x, end.y)
    return
  }

  try {
    await deviceApi.screenTap(deviceId.value, { x: end.x, y: end.y })
    uiTreeReady.value = false
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '点击设备失败'))
  }
}

async function pressNavKey(key) {
  if (!connected.value) return
  try {
    await deviceApi.screenKey(deviceId.value, { key })
  } catch {
    ElMessage.error('按键发送失败')
  }
}

async function connectStream() {
  updateLayoutSize()
  await nextTick()
  bindCanvasPipeline()
  await startStream()
  redrawLastFrame()
  uiTreeReady.value = false
}

function onWindowResize() {
  updateLayoutSize()
  redrawLastFrame()
}

watch(deviceId, async (id) => {
  if (!id) {
    loadDevices()
    return
  }
  initReviewContext()
  await loadDevice()
  nextTick(() => {
    bindCanvasPipeline()
    resumeIfAlive()
  })
}, { immediate: false })

onMounted(async () => {
  if (!deviceId.value) {
    loadDevices()
    return
  }
  initReviewContext()
  try {
    await deviceApi.syncUsb()
  } catch { /* ignore */ }
  await loadDevice()
  window.addEventListener('resize', onWindowResize)
  nextTick(() => {
    bindCanvasPipeline()
    resumeIfAlive()
    redrawLastFrame()
  })
})

onActivated(() => {
  if (deviceId.value) {
    updateLayoutSize()
    resumeIfAlive()
    nextTick(() => {
      bindCanvasPipeline()
      redrawLastFrame()
    })
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize)
  if (detachFrame) detachFrame()
  if (detachMeta) detachMeta()
  if (renderer) renderer.destroy()
})
</script>

<style scoped>
.picker-page {
  max-width: none;
}

.status-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-radius: 10px;
  margin-bottom: 16px;
  font-size: 13px;
}
.status-banner.success {
  background: #ecfdf5;
  color: #047857;
}
.status-banner.warning {
  background: #fffbeb;
  color: #92400e;
}
.status-banner.danger {
  background: #fff7ed;
  color: #9a3412;
}
.status-banner__text { flex: 1; min-width: 200px; }
.status-banner__actions { display: flex; gap: 8px; flex-wrap: wrap; }

.dev-name {
  font-weight: 700;
  color: var(--atp-text, #0f172a);
}
.dev-serial {
  font-size: 12px;
  color: #94a3b8;
}

.status-tag {
  font-weight: 700 !important;
  border-width: 1px !important;
}
.status-tag.is-online {
  background: #ecfdf5 !important;
  border-color: #6ee7b7 !important;
  color: #047857 !important;
}
.status-tag.is-online :deep(.el-tag__content) {
  color: #047857 !important;
}
.status-tag.is-offline {
  background: #fff7ed !important;
  border-color: #fdba74 !important;
  color: #9a3412 !important;
}
.status-tag.is-offline :deep(.el-tag__content) {
  color: #9a3412 !important;
}

.hub-empty {
  text-align: center;
  padding: 40px 16px;
  color: var(--atp-text-secondary);
}
.hub-empty p { margin: 0 0 16px; }
.hub-empty__actions { display: flex; justify-content: center; gap: 12px; flex-wrap: wrap; }

.screen-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
  font-weight: 600;
}
.screen-card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.basic-info-block { margin-bottom: 12px; }
.basic-info-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--atp-text);
}
.locator-schemes { margin: 12px 0; }
.locator-scheme-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--el-fill-color-blank);
}
.locator-scheme-card.recommend {
  border-color: color-mix(in srgb, var(--el-color-success) 40%, #fff);
  background: color-mix(in srgb, var(--el-color-success) 8%, #fff);
}
.scheme-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
}
.scheme-head em {
  font-style: normal;
  color: var(--el-color-success);
  font-weight: 500;
  margin-left: 4px;
}
.scheme-value {
  display: block;
  font-size: 12px;
  word-break: break-all;
  color: var(--atp-text-secondary);
}

.hist-name { font-weight: 600; color: var(--atp-text); }
.hist-meta {
  font-size: 11px;
  color: #94a3b8;
}
.history-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  gap: 8px;
}

.picker-workbench {
  display: grid;
  grid-template-columns: minmax(280px, 420px) minmax(360px, 1fr);
  gap: 20px;
  align-items: start;
}
@media (max-width: 960px) {
  .picker-workbench {
    grid-template-columns: 1fr;
  }
}
.screen-panel {
  position: sticky;
  top: 12px;
}
.screen-card :deep(.el-card__body) {
  padding-bottom: 12px;
}
.screen-wrap {
  line-height: 0;
  width: fit-content;
  margin: 0 auto;
}
.screen-device {
  display: flex;
  flex-direction: column;
  border-radius: var(--atp-radius-md, 12px);
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.15);
}
.screen-frame {
  position: relative;
  background: var(--atp-screen-bg, #0f172a);
  overflow: hidden;
  flex-shrink: 0;
  box-sizing: border-box;
  border: 2px solid var(--atp-dark-border, #334155);
}
.screen-canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: pointer;
  user-select: none;
  touch-action: none;
  vertical-align: top;
}
.screen-canvas.picking {
  cursor: crosshair;
}
.pick-marker {
  position: absolute;
  width: 14px;
  height: 14px;
  margin: -7px 0 0 -7px;
  border: 2px solid #f59e0b;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.35);
  pointer-events: none;
  z-index: 3;
}
.screen-placeholder {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--atp-screen-text-muted, #94a3b8);
  gap: 8px;
  font-size: 13px;
  background: var(--atp-screen-bg, #0f172a);
}
.frame-dim {
  font-size: 12px;
  opacity: 0.85;
}
.detail-panel {
  min-width: 0;
}
.pick-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.pick-toolbar :deep(.el-divider--vertical) {
  height: 1.2em;
  margin: 0 2px;
}
.pick-mode-row {
  margin-top: 12px;
}
.resolution-tag {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: auto;
}
.panel-block {
  margin-top: 10px;
}
.review-banner {
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
  font-size: 13px;
}
.action-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 8px 0 0;
  line-height: 1.5;
}
.inspecting-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-color-primary);
  font-size: 13px;
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.pick-result-card :deep(.el-card__body) {
  padding-top: 10px;
}
.pick-result-card {
  --pick-card-gap: 6px;
  --pick-card-pad: 8px 10px;
  --pick-card-radius: 8px;
  --pick-row-gap: 5px;
  --pick-surface: var(--el-fill-color-blank);
  --pick-surface-muted: var(--el-fill-color-light);
  --pick-border: var(--el-border-color-lighter);
  --pick-rec-bg: color-mix(in srgb, var(--el-color-success) 10%, var(--el-fill-color-blank));
  --pick-rec-border: color-mix(in srgb, var(--el-color-success) 24%, var(--el-border-color-lighter));
  --pick-sel-bg: color-mix(in srgb, var(--el-color-primary) 10%, var(--el-fill-color-blank));
  --pick-sel-border: color-mix(in srgb, var(--el-color-primary) 24%, var(--el-border-color-lighter));
  --pick-primary-row: color-mix(in srgb, var(--el-color-primary) 8%, var(--el-fill-color-blank));
  --pick-hover-shadow: 0 1px 6px color-mix(in srgb, var(--el-text-color-primary) 7%, transparent);
  --pick-badge-rec-bg: color-mix(in srgb, var(--el-color-success) 18%, var(--el-fill-color-light));
  --pick-badge-bg: color-mix(in srgb, var(--el-color-primary) 18%, var(--el-fill-color-light));
  --pick-rank-rec-bg: color-mix(in srgb, var(--el-color-success) 16%, var(--el-fill-color-light));
  --pick-validate-ok: color-mix(in srgb, var(--el-color-success) 12%, var(--el-fill-color-blank));
  --pick-validate-fail: color-mix(in srgb, var(--el-color-danger) 12%, var(--el-fill-color-blank));
  --pick-detail-bg: var(--el-fill-color-lighter);
}
.pick-summary {
  display: flex;
  flex-direction: column;
  gap: var(--pick-card-gap);
}
.pick-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.pick-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}
.pick-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pick-alert {
  margin: 0;
}
.pick-alert :deep(.el-alert) {
  padding: 6px 10px;
}
.primary-loc-card.recommended {
  background: var(--pick-rec-bg);
  border-color: var(--pick-rec-border);
}
.primary-loc-card.selected {
  background: var(--pick-sel-bg);
  border-color: var(--pick-sel-border);
}
.plc-badge.rec {
  color: var(--el-color-success);
  background: var(--pick-badge-rec-bg);
}
.primary-loc-card {
  padding: var(--pick-card-pad);
  border-radius: var(--pick-card-radius);
  border: 1px solid var(--pick-border);
}
.plc-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.plc-reason {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
.plc-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--pick-badge-bg);
  padding: 1px 7px;
  border-radius: 999px;
}
.plc-type {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.plc-value-row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}
.plc-value {
  flex: 1;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
  color: var(--el-text-color-primary);
  background: transparent;
}
.pick-desc {
  margin-top: 0;
}
.pick-desc :deep(.el-descriptions__cell) {
  padding: 4px 8px !important;
}
.pick-desc :deep(.el-descriptions__label) {
  width: 72px;
}
.loc-chain-section {
  margin-top: 2px;
}
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 5px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.section-sub {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.loc-list {
  display: flex;
  flex-direction: column;
  gap: var(--pick-row-gap);
}
.loc-item {
  display: grid;
  grid-template-columns: 24px 1fr auto;
  gap: 8px;
  align-items: start;
  padding: 7px 9px;
  border-radius: var(--pick-card-radius);
  border: 1px solid var(--pick-border);
  background: var(--pick-surface);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, opacity 0.15s, background 0.15s;
}
.loc-item:hover {
  border-color: color-mix(in srgb, var(--el-color-primary) 35%, var(--pick-border));
  box-shadow: var(--pick-hover-shadow);
}
.loc-item.primary {
  border-color: color-mix(in srgb, var(--el-color-primary) 35%, var(--pick-border));
  background: var(--pick-primary-row);
}
.loc-item.off {
  opacity: 0.55;
}
.loc-rank {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: var(--pick-surface-muted);
  color: var(--el-text-color-secondary);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}
.loc-item.primary .loc-rank {
  background: var(--el-color-primary);
  color: #fff;
}
.loc-item.recommended:not(.primary) {
  border-left: 2px solid color-mix(in srgb, var(--el-color-success) 55%, var(--pick-border));
}
.loc-rank.rec {
  background: var(--pick-rank-rec-bg);
  color: var(--el-color-success);
}
.loc-item.primary .loc-rank.rec {
  background: var(--el-color-primary);
  color: #fff;
}
.loc-main {
  min-width: 0;
}
.loc-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 2px;
}
.loc-type {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}
.loc-value {
  display: block;
  font-size: 11px;
  line-height: 1.45;
  word-break: break-all;
  color: var(--el-text-color-primary);
  background: transparent;
}
.loc-value.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.loc-expand {
  margin-top: 4px;
  padding: 0;
  border: none;
  background: none;
  color: var(--el-color-primary);
  font-size: 11px;
  cursor: pointer;
}
.loc-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.loc-order {
  display: flex;
}
.validate-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}
.relative-section {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--pick-border);
}
.section-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}
.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.section-sub {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.relative-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.relative-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  border: 1px solid var(--pick-border);
  border-radius: 6px;
  background: var(--pick-surface-muted);
}
.rc-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.rc-preview {
  font-size: 10px;
  line-height: 1.4;
  word-break: break-all;
  color: var(--el-text-color-secondary);
  background: transparent;
}
.region-inner {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
@media (max-width: 1200px) {
  .relative-grid {
    grid-template-columns: 1fr;
  }
}
.validate-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: var(--pick-card-radius);
  font-size: 12px;
}
.validate-banner.ok {
  background: var(--pick-validate-ok);
  color: var(--el-color-success);
}
.validate-banner.fail {
  background: var(--pick-validate-fail);
  color: var(--el-color-danger);
}
.validate-detail {
  margin-top: 4px;
  padding: 6px 8px;
  border-radius: var(--pick-card-radius);
  background: var(--pick-detail-bg);
  font-size: 11px;
}
.validate-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 4px;
}
.validate-line .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-danger);
  margin-top: 4px;
  flex-shrink: 0;
}
.validate-line .dot.ok {
  background: var(--el-color-success);
}
.validate-line .dot.warn {
  background: var(--el-color-warning);
}
.validate-line .validate-status {
  flex: 0 0 52px;
  font-size: 10px;
  color: var(--el-text-color-secondary);
}
.validate-line .vt {
  flex: 0 0 72px;
  color: var(--el-text-color-secondary);
}
.validate-line code {
  flex: 1;
  word-break: break-all;
}
.pick-actions-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--pick-border);
}
.save-loc-preview {
  display: block;
  word-break: break-all;
  font-size: 11px;
  background: var(--pick-surface-muted);
  padding: 5px 7px;
  border-radius: 4px;
}
.history-row {
  display: flex;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  margin-bottom: 4px;
}
.history-row:hover,
.history-row.active {
  background: var(--el-fill-color-light);
}
.hist-no {
  flex: 0 0 20px;
  color: var(--el-text-color-secondary);
}
.hist-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.hist-loc {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-color-scheme: dark) {
  .pick-result-card {
    --pick-surface: #161b22;
    --pick-surface-muted: #1c2430;
    --pick-border: #2f3a4a;
    --pick-detail-bg: #1c2430;
    --pick-rec-bg: color-mix(in srgb, var(--el-color-success) 14%, #161b22);
    --pick-rec-border: color-mix(in srgb, var(--el-color-success) 32%, #2f3a4a);
    --pick-sel-bg: color-mix(in srgb, var(--el-color-primary) 14%, #161b22);
    --pick-sel-border: color-mix(in srgb, var(--el-color-primary) 32%, #2f3a4a);
    --pick-primary-row: color-mix(in srgb, var(--el-color-primary) 12%, #161b22);
    --pick-badge-rec-bg: color-mix(in srgb, var(--el-color-success) 22%, #1c2430);
    --pick-badge-bg: color-mix(in srgb, var(--el-color-primary) 22%, #1c2430);
    --pick-rank-rec-bg: color-mix(in srgb, var(--el-color-success) 20%, #1c2430);
    --pick-validate-ok: color-mix(in srgb, var(--el-color-success) 16%, #161b22);
    --pick-validate-fail: color-mix(in srgb, var(--el-color-danger) 16%, #161b22);
    --pick-hover-shadow: 0 1px 6px rgba(0, 0, 0, 0.35);
  }
}

:global(html.dark) .pick-result-card {
  --pick-surface: var(--el-bg-color-overlay, #1d1e1f);
  --pick-surface-muted: var(--el-fill-color-light, #262727);
  --pick-border: var(--el-border-color-lighter, #414243);
  --pick-detail-bg: var(--el-fill-color-lighter, #2b2b2c);
  --pick-rec-bg: color-mix(in srgb, var(--el-color-success) 14%, var(--el-bg-color, #141414));
  --pick-rec-border: color-mix(in srgb, var(--el-color-success) 30%, var(--el-border-color-lighter, #414243));
  --pick-sel-bg: color-mix(in srgb, var(--el-color-primary) 14%, var(--el-bg-color, #141414));
  --pick-sel-border: color-mix(in srgb, var(--el-color-primary) 30%, var(--el-border-color-lighter, #414243));
  --pick-primary-row: color-mix(in srgb, var(--el-color-primary) 12%, var(--el-bg-color, #141414));
  --pick-badge-rec-bg: color-mix(in srgb, var(--el-color-success) 20%, var(--el-fill-color-light, #262727));
  --pick-badge-bg: color-mix(in srgb, var(--el-color-primary) 20%, var(--el-fill-color-light, #262727));
  --pick-rank-rec-bg: color-mix(in srgb, var(--el-color-success) 18%, var(--el-fill-color-light, #262727));
  --pick-validate-ok: color-mix(in srgb, var(--el-color-success) 15%, var(--el-bg-color, #141414));
  --pick-validate-fail: color-mix(in srgb, var(--el-color-danger) 15%, var(--el-bg-color, #141414));
  --pick-hover-shadow: 0 1px 6px rgba(0, 0, 0, 0.4);
}
</style>
