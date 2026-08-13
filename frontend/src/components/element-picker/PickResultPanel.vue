<template>
  <div class="pick-result-panel" :class="{ dimmed: inspecting }">
    <div v-if="inspecting" class="inspecting-overlay">
      <el-icon class="spin"><Loading /></el-icon>
      识别中，请稍候…
    </div>

    <el-tabs v-model="panelMode" class="prp-mode-tabs" @tab-change="onPanelModeChange">
      <el-tab-pane name="current">
        <template #label>
          <span>当前控件</span>
        </template>

        <template v-if="currentPick">
      <!-- 模块1：顶部通栏 -->
      <header class="prp-header">
        <div class="prp-header-main">
          <h3 class="prp-title">
            控件定位拾取结果
            <span class="prp-title-sep">｜</span>
            <span class="prp-title-name">{{ displayName }}</span>
          </h3>
          <div class="prp-status-tags">
            <el-tag :type="recognizeStatus.type" size="small" effect="dark" round>
              {{ recognizeStatus.label }}
            </el-tag>
            <el-tag
              v-if="currentPick.risk_level"
              :type="riskTagType(currentPick.risk_level)"
              size="small"
              effect="dark"
              round
            >
              {{ riskLevelLabel(currentPick.risk_level) }}
            </el-tag>
            <el-tag
              v-if="stabilityScore != null"
              :type="stabilityScoreType(stabilityScore)"
              size="small"
              effect="plain"
              round
            >
              稳定度 {{ stabilityScore }}
            </el-tag>
            <el-tag v-if="hasScreenRatioLocator" type="success" size="small" effect="plain" round>
              🟢✅已生成屏幕比例坐标
            </el-tag>
            <el-tag v-if="currentPick.dump_source" size="small" :type="currentPick.dump_source === 'u2' ? 'success' : 'warning'" effect="plain" round>
              树：{{ dumpSourceLabel }}
            </el-tag>
            <el-tag v-if="currentPick.strategy_used" size="small" type="info" effect="plain" round>
              策略：{{ strategyUsedLabel }}
            </el-tag>
          </div>
        </div>
        <div class="prp-global-actions">
          <el-button size="small" @click="$emit('copy-all')">复制全部定位信息</el-button>
          <el-button
            size="small"
            type="warning"
            plain
            :loading="validating"
            :disabled="!connected || !locatorChain.length"
            @click="$emit('validate')"
          >
            验证定位
          </el-button>
          <el-button size="small" type="primary" @click="$emit('save-pool')">保存至公共控件库</el-button>
          <el-button size="small" type="primary" plain :disabled="!currentPick" @click="$emit('create-case')">一键录制</el-button>
          <el-button
            v-if="pickRecordId != null && pickStepIndex != null"
            size="small"
            type="success"
            :loading="applying"
            @click="$emit('apply-review')"
          >
            应用到审阅
          </el-button>
        </div>
      </header>

      <el-alert
        v-if="currentPick.risk_reasons?.length"
        :title="currentPick.risk_reasons[0]"
        :description="currentPick.risk_reasons.length > 1 ? currentPick.risk_reasons.slice(1).join('；') : undefined"
        type="warning"
        :closable="false"
        show-icon
        class="prp-alert"
      />
      <el-alert
        v-if="currentPick.auto_context_switched && currentPick.context_hint"
        :title="currentPick.context_hint"
        type="success"
        :closable="false"
        show-icon
        class="prp-alert"
      />
      <el-alert
        v-else-if="currentPick.needs_context_switch"
        title="检测到 WebView 混合页面，拾取时已尝试自动切换；仍异常可手动切换"
        type="warning"
        :closable="false"
        show-icon
        class="prp-alert"
      />

      <div class="prp-workspace">
        <!-- 左侧大纲导航 -->
        <aside class="prp-outline">
          <div class="outline-title">导航大纲</div>
          <button
            v-for="item in outlineItems"
            :key="item.id"
            type="button"
            class="outline-item"
            :class="{ active: activeOutline === item.id, star: item.star }"
            @click="onOutlineClick(item)"
          >
            <span class="outline-icon">{{ item.star ? '⭐' : '📌' }}</span>
            <span class="outline-label">{{ item.label }}</span>
          </button>
        </aside>

        <!-- 右侧主内容 -->
        <div class="prp-main">
          <el-tabs v-model="activeTab" class="prp-tabs" @tab-change="onTabChange">
            <!-- Tab1：定位策略方案 -->
            <el-tab-pane label="定位策略方案" name="strategy">
              <div class="tab-scroll" ref="strategyScrollRef">
                <!-- 子卡片①：控件基础信息 -->
                <section id="sec-basic" class="prp-section">
                  <div class="sec-title">控件基础信息</div>
                  <table class="info-table compact">
                    <tbody>
                      <tr>
                        <th>控件名称</th>
                        <td colspan="3">{{ displayName }}</td>
                      </tr>
                      <tr>
                        <th>控件类型</th>
                        <td>{{ widgetTypeDisplay }}</td>
                        <th>类名</th>
                        <td>{{ currentPick.class ? shortClass(currentPick.class) : '-' }}</td>
                      </tr>
                      <tr>
                        <th>点击基准坐标</th>
                        <td>
                          <template v-if="currentPick.inspect_x != null">
                            {{ currentPick.inspect_x }}, {{ currentPick.inspect_y }}
                          </template>
                          <template v-else>{{ currentPick.x }}, {{ currentPick.y }}</template>
                        </td>
                        <th>控件边界</th>
                        <td>{{ currentPick.bounds || '-' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </section>

                <!-- 子卡片②：推荐方案 -->
                <section id="sec-recommend" class="prp-section recommend-hero">
                  <div class="sec-title recommend-title">
                    <span>⭐ 推荐方案：{{ recommendedLabel }}</span>
                    <el-button
                      v-if="recommendedLocatorItem?.value"
                      type="primary"
                      size="small"
                      @click="$emit('copy-text', recommendedLocatorItem.value)"
                    >
                      一键复制
                    </el-button>
                  </div>
                  <template v-if="recommendedLocatorItem">
                    <div class="recommend-meta">
                      <span class="meta-label">定位方式</span>
                      <span>{{ formatLocatorType(normalizeLocatorType(recommendedLocatorItem.type)) }}</span>
                    </div>
                    <code class="recommend-expr">{{ recommendedLocatorItem.value }}</code>
                    <p class="recommend-desc">
                      {{ recommendReasonText || '适配分辨率变动，布局改动抗干扰能力最强，录制首选' }}
                    </p>
                  </template>
                  <el-empty v-else description="暂无推荐定位方案" :image-size="48" />
                </section>

                <!-- 子卡片③：备选方案（默认折叠） -->
                <section id="sec-alts" class="prp-section">
                  <el-collapse v-model="altCollapse">
                    <el-collapse-item name="alts">
                      <template #title>
                        <span class="collapse-title">备选定位方案</span>
                        <span class="collapse-hint">{{ availableAltCount }}/{{ alternativeSchemes.length }} 可用</span>
                      </template>
                      <div
                        v-for="(scheme, sIdx) in alternativeSchemes"
                        :key="scheme.key + '-' + sIdx"
                        class="alt-row"
                        :class="{ empty: !scheme.value, recommend: scheme.recommended }"
                      >
                        <span class="alt-name">
                          <i class="alt-pri">{{ sIdx + 1 }}</i>
                          {{ scheme.label }}
                          <em v-if="scheme.recommended">推荐</em>
                        </span>
                        <span v-if="scheme.pass_rate != null" class="alt-rate" :class="passRateTone(scheme.pass_rate)">
                          {{ formatPassRate(scheme.pass_rate) }}
                        </span>
                        <code class="alt-expr">{{ scheme.value || '暂无方案' }}</code>
                        <el-button
                          size="small"
                          plain
                          :disabled="!scheme.value"
                          @click="$emit('copy-text', scheme.value)"
                        >
                          一键复制
                        </el-button>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </section>

                <!-- 定位链策略管理 -->
                <section id="sec-chain" class="prp-section chain-section">
                  <el-collapse v-model="chainCollapse">
                    <el-collapse-item name="chain">
                      <template #title>
                        <span class="collapse-title">定位链策略管理（自动降级兜底）</span>
                        <span class="collapse-hint">{{ enabledChainCount }}/{{ locatorChain.length }} 启用</span>
                      </template>
                      <p class="chain-tip">
                        定位链按预估通过率从高到低排序（优先级 1 最优先）；验证后会按实际命中结果再调整。可开关、手动排序，写入用例后执行引擎按此顺序降级。
                      </p>
                      <div v-if="locatorChain.length" class="chain-table">
                        <div class="chain-table-head">
                          <span class="col-pri">优先级</span>
                          <span class="col-name">定位方案名称</span>
                          <span class="col-rate">通过率</span>
                          <span class="col-expr">定位表达式</span>
                          <span class="col-enable">启用</span>
                          <span class="col-sort">排序</span>
                        </div>
                        <div
                          v-for="(item, idx) in locatorChain"
                          :key="item.type + item.value + idx"
                          class="chain-table-row"
                          :class="{ recommend: item.recommended, primary: item.primary, off: item.enabled === false }"
                          @click="$emit('set-primary', idx)"
                        >
                          <span class="col-pri">{{ item.priority || idx + 1 }}</span>
                          <span class="col-name">
                            {{ formatLocatorType(normalizeLocatorType(item.type)) }}
                            <em v-if="item.recommended">推荐</em>
                          </span>
                          <span class="col-rate" :class="passRateTone(item.pass_rate)">
                            {{ formatPassRate(item.pass_rate) }}
                          </span>
                          <code class="col-expr" :title="item.value">{{ item.value }}</code>
                          <span class="col-enable" @click.stop>
                            <el-switch
                              :model-value="item.enabled !== false"
                              size="small"
                              @change="(v) => onToggleEnabled(idx, v)"
                            />
                          </span>
                          <span class="col-sort" @click.stop>
                            <el-button size="small" text :icon="ArrowUp" :disabled="idx === 0" @click="$emit('move-chain', idx, -1)" />
                            <el-button size="small" text :icon="ArrowDown" :disabled="idx >= locatorChain.length - 1" @click="$emit('move-chain', idx, 1)" />
                          </span>
                        </div>
                      </div>
                      <el-empty v-else description="暂无定位链条目" :image-size="40" />
                      <div class="chain-footer">
                        <el-button
                          size="small"
                          type="warning"
                          plain
                          :loading="validating"
                          :disabled="!connected || !locatorChain.length"
                          @click="$emit('validate')"
                        >
                          验证定位
                        </el-button>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </section>

                <!-- 验证结果 -->
                <div v-if="currentPick?.validate_result || validateResult" class="validate-block">
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
                </div>
              </div>
            </el-tab-pane>

            <!-- Tab2：高级配置 -->
            <el-tab-pane label="高级配置 - 相对定位" name="advanced">
              <div class="tab-scroll" ref="advancedScrollRef">
                <section id="sec-relative" class="prp-section">
                  <div class="sec-title">相对锚点 / 区域限定</div>
                  <p class="sec-desc">适配浮动控件、局部控件；加入定位链后可在「定位策略方案」Tab 底部管理优先级。</p>
                  <div class="relative-cols">
                    <div class="relative-col">
                      <div class="relative-col-title">容器锚点 - 下标</div>
                      <el-input v-model="relativeForm.container" size="small" placeholder="resource-id 别名" />
                      <el-input-number v-model="relativeForm.index" size="small" :min="0" :max="99" controls-position="right" style="width:100%" />
                      <el-button
                        size="small"
                        type="primary"
                        plain
                        :disabled="!relativePreview.parent_index"
                        @click="$emit('add-relative', 'parent_index')"
                      >
                        加入定位链
                      </el-button>
                    </div>
                    <div class="relative-col">
                      <div class="relative-col-title">锚点定位</div>
                      <el-input v-model="relativeForm.anchor" size="small" placeholder="设备文本 / content-desc" />
                      <el-select v-model="relativeForm.direction" size="small" style="width:100%">
                        <el-option v-for="d in ANCHOR_DIRECTIONS" :key="d.value" :label="d.label" :value="d.value" />
                      </el-select>
                      <el-button
                        size="small"
                        type="primary"
                        plain
                        :disabled="!relativePreview.anchor_adjacent"
                        @click="$emit('add-relative', 'anchor_adjacent')"
                      >
                        加入定位链
                      </el-button>
                    </div>
                    <div class="relative-col">
                      <div class="relative-col-title">区域限定</div>
                      <el-input v-model="relativeForm.region_bounds" size="small" placeholder="边界坐标 [x1,y1][x2,y2]" />
                      <div class="region-inner">
                        <el-select v-model="relativeForm.inner_type" size="small" style="width:110px">
                          <el-option label="Content-Desc" value="content_desc" />
                          <el-option label="ID" value="id" />
                          <el-option label="文本" value="text" />
                        </el-select>
                        <el-input v-model="relativeForm.inner_value" size="small" placeholder="匹配值" />
                      </div>
                      <el-button
                        size="small"
                        type="primary"
                        plain
                        :disabled="!relativePreview.region_locator"
                        @click="$emit('add-relative', 'region_locator')"
                      >
                        加入定位链
                      </el-button>
                    </div>
                  </div>
                </section>
              </div>
            </el-tab-pane>

            <!-- Tab3：手动自定义 -->
            <el-tab-pane label="手动自定义控件参数" name="manual">
              <div class="tab-scroll" ref="manualScrollRef">
                <section id="sec-manual" class="prp-section">
                  <div class="sec-title">手动自定义控件参数</div>
                  <p class="sec-desc">自动拾取不准时在此修正；与系统识别方案分开，避免混淆。</p>
                  <el-form label-width="88px" size="small" class="manual-form">
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
                      <el-input v-model="manualForm.locator_value" type="textarea" :rows="3" placeholder="Resource ID / 文本 / XPath" />
                    </el-form-item>
                    <el-form-item>
                      <el-button type="primary" :disabled="!manualForm.locator_value" @click="$emit('apply-manual')">
                        应用到当前控件
                      </el-button>
                    </el-form-item>
                  </el-form>
                </section>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
        </template>

        <el-empty v-else description="切换到「识别控件」并单击画面拾取" :image-size="72" />
      </el-tab-pane>

      <el-tab-pane name="history">
        <template #label>
          <span>拾取历史<span v-if="pickHistory.length" class="hist-badge">{{ pickHistory.length }}</span></span>
        </template>
        <section id="sec-history" class="prp-history-panel">
          <div class="history-panel-head">
            <div>
              <div class="history-panel-title">本轮拾取历史</div>
              <p class="history-panel-desc">点击任意记录可回看定位信息；可一键批量入库到控件库。</p>
            </div>
            <div class="history-panel-actions">
              <el-button
                v-if="pickHistory.length"
                size="small"
                type="primary"
                plain
                @click="$emit('restore-history-pool')"
              >
                批量入库
              </el-button>
              <el-button
                v-if="pickHistory.length"
                size="small"
                type="danger"
                plain
                @click="$emit('clear-history')"
              >
                清空全部历史
              </el-button>
            </div>
          </div>
          <el-scrollbar max-height="520px">
            <div
              v-for="(item, idx) in pickHistory"
              :key="item.id || `${item.picked_at || ''}-${idx}`"
              class="history-row"
              :class="{ active: currentPick?.id === item.id }"
              @click="onSelectHistory(item)"
            >
              <span class="hist-no">{{ pickHistory.length - idx }}</span>
              <div class="hist-body">
                <span class="hist-name">{{ item.display_name || item.element_name || `控件 (${item.x},${item.y})` }}</span>
                <span class="hist-meta">所属设备：{{ item.device_label || deviceLabel || '-' }}</span>
                <span class="hist-meta">拾取时间：{{ formatPickTime(item.picked_at) }}</span>
                <span v-if="formatStepLocator(item)" class="hist-loc">{{ formatStepLocator(item) }}</span>
              </div>
              <el-tag v-if="currentPick?.id === item.id" size="small" type="primary" effect="plain">当前</el-tag>
            </div>
            <el-empty v-if="!pickHistory.length" description="本轮尚无历史拾取" :image-size="56" />
          </el-scrollbar>
          <div class="history-footer">
            <el-button type="primary" link @click="$emit('goto-pool')">查看全部公共控件库</el-button>
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <QuickRecordFab />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { Loading, ArrowUp, ArrowDown, CircleCheck, WarningFilled } from '@element-plus/icons-vue'
import { formatStepLocator, formatLocatorType } from '@/utils/stepDisplay'
import {
  riskLevelLabel, riskTagType, isWeakPick,
  ANCHOR_DIRECTIONS,
  buildParentIndexValue, buildAnchorAdjacentValue, buildRegionLocatorValue,
  computeStabilityScore, stabilityScoreType, recommendReasonLabel
} from '@/utils/locatorAssist'
import QuickRecordFab from '@/components/QuickRecordFab.vue'

const props = defineProps({
  currentPick: { type: Object, default: null },
  locatorChain: { type: Array, default: () => [] },
  relativeForm: { type: Object, required: true },
  manualForm: { type: Object, required: true },
  pickHistory: { type: Array, default: () => [] },
  inspecting: { type: Boolean, default: false },
  validating: { type: Boolean, default: false },
  applying: { type: Boolean, default: false },
  connected: { type: Boolean, default: false },
  validateResult: { type: Object, default: null },
  validateAttempts: { type: Array, default: () => [] },
  pickRecordId: { type: [Number, String], default: null },
  pickStepIndex: { type: [Number, String], default: null },
  deviceLabel: { type: String, default: '' },
  formatValidateMeta: { type: Function, required: true }
})

const emit = defineEmits([
  'copy-all', 'validate', 'save-pool', 'create-case', 'apply-review', 'copy-text',
  'set-primary', 'move-chain', 'toggle-enabled', 'add-relative',
  'apply-manual', 'select-history', 'clear-history', 'restore-history-pool', 'goto-pool', 'sync-chain',
  'panel-mode-change'
])

const activeTab = ref('strategy')
const panelMode = ref('current')
const activeOutline = ref('basic')
const altCollapse = ref([])
const chainCollapse = ref(['chain'])
const showValidateDetail = ref(false)
const strategyScrollRef = ref(null)
const advancedScrollRef = ref(null)
const manualScrollRef = ref(null)

const RECOMMEND_DESC = {
  content_desc: '适配分辨率变动，布局改动抗干扰能力最强，录制首选',
  text: '依托可见文本，适配多分辨率，抗布局微调，稳定性较高',
  id: '依托唯一 resource-id，跨版本稳定，优先推荐',
  resource_id: '依托唯一 resource-id，跨版本稳定，优先推荐',
  xpath: '通用路径定位，适配结构相似页面，稳定性中等',
  xpath_desc: '基于描述的相对路径，兼顾语义与结构',
  relative_xpath: '短结构路径，比绝对路径更耐布局微调',
  absolute_xpath: '从根节点写死的完整路径，结构一变易失效',
  class_name: '仅依赖类名，易受布局复用影响，稳定性偏低',
  bounds: '固定坐标区域，分辨率变化时需校准',
  screen_ratio: '屏幕比例坐标，适配多分辨率兜底',
  ocr: 'OCR 文本识别，适合自绘/无障碍稀疏界面'
}

const outlineItems = computed(() => [
  { id: 'basic', label: '控件基础信息', tab: 'strategy', target: 'sec-basic' },
  { id: 'strategy', label: '定位策略方案', tab: 'strategy', target: 'sec-recommend' },
  {
    id: 'recommend',
    label: `推荐方案：${recommendedLabel.value}`,
    tab: 'strategy',
    target: 'sec-recommend',
    star: true
  },
  { id: 'alts', label: '备选方案列表', tab: 'strategy', target: 'sec-alts', expandAlts: true },
  { id: 'relative', label: '高级配置 - 相对锚点/区域限定', tab: 'advanced', target: 'sec-relative' },
  { id: 'chain', label: '定位链策略管理（自动降级兜底）', tab: 'strategy', target: 'sec-chain', expandChain: true },
  { id: 'manual', label: '手动自定义控件参数', tab: 'manual', target: 'sec-manual' },
  { id: 'history', label: '拾取历史', tab: null, target: 'sec-history', openHistory: true }
])

const displayName = computed(() =>
  props.currentPick?.display_name || props.currentPick?.element_name || '未命名控件'
)

const recognizeStatus = computed(() => {
  const pick = props.currentPick
  if (!pick) return { label: '未拾取', type: 'info' }
  if (pick.valid && !isWeakPick(pick)) return { label: '已识别', type: 'success' }
  if (isWeakPick(pick)) return { label: '高风险', type: 'danger' }
  return { label: '需补充', type: 'warning' }
})

const stabilityScore = computed(() => computeStabilityScore(props.currentPick))

const hasScreenRatioLocator = computed(() => {
  const pick = props.currentPick
  if (!pick) return false
  if (pick.locators?.screen_ratio) return true
  return (props.locatorChain || []).some(i => i.type === 'screen_ratio' && i.value)
})

const dumpSourceLabel = computed(() => {
  const map = { u2: 'u2层级', shell: 'shell回退', cache: '缓存', fail: '失败' }
  return map[props.currentPick?.dump_source] || props.currentPick?.dump_source || '-'
})

const strategyUsedLabel = computed(() => {
  const map = { ui_tree: 'UI树', ocr: 'OCR', coordinate: '坐标比例', pending_refresh: '待刷新' }
  return map[props.currentPick?.strategy_used] || props.currentPick?.strategy_used || '-'
})

const widgetTypeDisplay = computed(() => {
  const pick = props.currentPick
  if (!pick) return '-'
  const step = String(pick.suggested_step_type || '').toLowerCase()
  if (step === 'tap' || step === 'click' || pick.clickable === true) return 'click 点击控件'
  if (step === 'input' || step === 'send_keys') return 'input 输入控件'
  if (step === 'tap_ocr') return 'ocr 点击控件'
  return widgetTypeLabel(pick.widget_type || pick.class)
})

const recommendedLocatorItem = computed(() =>
  props.locatorChain.find(i => i.recommended) || props.locatorChain[0] || null
)

const recommendedLabel = computed(() => {
  const rec = recommendedLocatorItem.value
  if (!rec) return '暂无'
  return formatLocatorType(normalizeLocatorType(rec.type))
})

const recommendReasonText = computed(() => {
  const pick = props.currentPick
  if (pick?.recommend_reason) return pick.recommend_reason
  const rec = recommendedLocatorItem.value
  if (!rec) return ''
  return rec.recommend_reason || RECOMMEND_DESC[rec.type] || recommendReasonLabel(rec.type)
})

const enabledChainCount = computed(() => props.locatorChain.filter(i => i.enabled !== false).length)

const relativePreview = computed(() => ({
  parent_index: buildParentIndexValue(props.relativeForm.container, props.relativeForm.index),
  anchor_adjacent: buildAnchorAdjacentValue(props.relativeForm.anchor, props.relativeForm.direction),
  region_locator: buildRegionLocatorValue(
    props.relativeForm.region_bounds,
    props.relativeForm.inner_type,
    props.relativeForm.inner_value
  )
}))

const alternativeSchemes = computed(() => {
  const chain = props.locatorChain || []
  // 与定位链一致：已按通过率排好，跳过与推荐完全同值的重复强调项仍保留列表
  if (chain.length) {
    return chain.map(item => ({
      key: item.type,
      label: formatLocatorType(normalizeLocatorType(item.type)),
      value: item.value || '',
      pass_rate: item.pass_rate,
      recommended: !!item.recommended
    }))
  }
  const pick = props.currentPick
  const locs = pick?.locators || {}
  const findVal = (...keys) => {
    for (const k of keys) {
      if (locs[k]) return String(locs[k])
    }
    return ''
  }
  return [
    { key: 'xpath', label: 'xpath', value: findVal('xpath', 'xpath_desc', 'relative_xpath', 'absolute_xpath') },
    { key: 'id', label: 'ID 定位', value: findVal('id', 'resource_id') },
    { key: 'text', label: '文本定位', value: findVal('text') },
    { key: 'class_name', label: '类名定位', value: findVal('class_name') || (pick?.class ? shortClass(pick.class) : '') },
    { key: 'bounds', label: '坐标定位', value: findVal('bounds') || pick?.bounds || '' }
  ].map(s => ({ ...s, recommended: false }))
})

const availableAltCount = computed(() => alternativeSchemes.value.filter(s => !!s.value).length)

const validateResultText = computed(() => {
  const r = props.validateResult
  if (!r) return ''
  if (r.valid) return `校验通过 · 可点击 · ${formatLocatorType(r.matched_by)}`
  if (r.error === 'not_clickable') return `存在但不可点击 · ${formatLocatorType(r.matched_by || '')}`
  return r.error || '校验未通过'
})

function normalizeLocatorType(type) {
  return type === 'resource_id' ? 'id' : type
}

function shortClass(clazz) {
  return String(clazz || '').split('.').pop() || clazz
}

function truncateLocator(val, max = 48) {
  const s = String(val || '')
  return s.length <= max ? s : `${s.slice(0, max - 3)}...`
}

function formatPassRate(rate) {
  if (rate == null || rate === '') return '-'
  const n = Number(rate)
  if (!Number.isFinite(n)) return '-'
  return `${Math.round(n)}%`
}

function passRateTone(rate) {
  const n = Number(rate)
  if (!Number.isFinite(n)) return 'is-low'
  if (n >= 75) return 'is-high'
  if (n >= 50) return 'is-mid'
  return 'is-low'
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

function validateAttemptLabel(att) {
  if (att.clickable) return '可点击'
  if (att.found && att.visible) return '不可点'
  if (att.found) return '不可见'
  return att.reason === 'not_found' ? '未命中' : (att.reason || '未命中')
}

function onToggleEnabled(idx, enabled) {
  emit('toggle-enabled', idx, enabled)
}

async function onOutlineClick(item) {
  activeOutline.value = item.id
  if (item.expandAlts) altCollapse.value = ['alts']
  if (item.expandChain) chainCollapse.value = ['chain']
  if (item.openHistory) {
    panelMode.value = 'history'
    emit('panel-mode-change', 'history')
    return
  }
  panelMode.value = 'current'
  emit('panel-mode-change', 'current')
  if (item.tab) activeTab.value = item.tab
  await nextTick()
  const el = document.getElementById(item.target)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onPanelModeChange(name) {
  if (name === 'history') activeOutline.value = 'history'
  else if (activeOutline.value === 'history') activeOutline.value = 'basic'
  emit('panel-mode-change', name === 'history' ? 'history' : 'current')
}

function onSelectHistory(item) {
  emit('select-history', item)
  panelMode.value = 'current'
  emit('panel-mode-change', 'current')
}

function onTabChange(name) {
  if (name === 'strategy') activeOutline.value = 'basic'
  else if (name === 'advanced') activeOutline.value = 'relative'
  else if (name === 'manual') activeOutline.value = 'manual'
}

watch(() => props.currentPick?.id, () => {
  activeTab.value = 'strategy'
  panelMode.value = 'current'
  emit('panel-mode-change', 'current')
  altCollapse.value = []
  showValidateDetail.value = false
})

function focusChainSection() {
  panelMode.value = 'current'
  return onOutlineClick(outlineItems.value.find(i => i.id === 'chain') || {
    id: 'chain',
    tab: 'strategy',
    target: 'sec-chain',
    expandChain: true
  })
}

function switchPanelMode(mode) {
  panelMode.value = mode === 'history' ? 'history' : 'current'
  emit('panel-mode-change', panelMode.value)
}

defineExpose({ focusChainSection, switchPanelMode })
</script>

<style scoped>
.pick-result-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 10px;
}
.pick-result-panel.dimmed > :not(.inspecting-overlay) {
  opacity: 0.55;
  pointer-events: none;
}
.inspecting-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.72);
  font-size: 14px;
  color: #334155;
  border-radius: 10px;
}
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.prp-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px 16px;
  padding: 12px 14px;
  background: linear-gradient(180deg, #f8fbff 0%, #fff 100%);
  border: 1px solid #dbeafe;
  border-radius: 12px;
}
.prp-header-main { flex: 1; min-width: 220px; }
.prp-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.4;
}
.prp-title-sep { color: #94a3b8; margin: 0 4px; font-weight: 500; }
.prp-title-name { color: #2563eb; word-break: break-all; }
.prp-status-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.prp-global-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.prp-alert { margin: 0; }

.prp-workspace {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 12px;
  min-height: 360px;
  align-items: stretch;
}
.prp-outline {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  padding: 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  position: sticky;
  top: 0;
  align-self: start;
  max-height: calc(100vh - 160px);
  overflow: auto;
}
.outline-title {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  padding: 4px 8px 8px;
}
.outline-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  color: #334155;
  font-size: 12px;
  line-height: 1.35;
}
.outline-item:hover { background: #e2e8f0; }
.outline-item.active {
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 600;
}
.outline-item.star .outline-label { font-weight: 600; }
.outline-icon { flex-shrink: 0; font-size: 11px; line-height: 1.35; }
.outline-label { flex: 1; }

.prp-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  padding: 8px 12px 12px;
  overflow: hidden;
}
.prp-tabs :deep(.el-tabs__header) { margin-bottom: 10px; }
.prp-tabs :deep(.el-tabs__content) { overflow: visible; }
.tab-scroll {
  max-height: min(62vh, 720px);
  overflow: auto;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.prp-section {
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 12px;
  background: #fff;
}
.sec-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 10px;
}
.sec-desc {
  margin: -4px 0 12px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.info-table.compact {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.info-table th,
.info-table td {
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  vertical-align: top;
}
.info-table th {
  width: 96px;
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
  white-space: nowrap;
}
.info-table td {
  color: #0f172a;
  word-break: break-all;
}

.recommend-hero {
  background: linear-gradient(180deg, #ecfdf5 0%, #f0fdf4 100%);
  border-color: #86efac;
  box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.12);
}
.recommend-title { color: #166534; }
.recommend-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 8px;
  color: #334155;
}
.meta-label { color: #64748b; font-weight: 600; }
.recommend-expr {
  display: block;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
  color: #14532d;
}
.recommend-desc {
  margin: 8px 0 0;
  font-size: 12px;
  color: #3f6212;
  line-height: 1.5;
}

.collapse-title { font-weight: 700; font-size: 13px; color: #0f172a; }
.collapse-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 400;
}
.alt-row {
  display: grid;
  grid-template-columns: 130px 52px 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}
.alt-row:last-child { border-bottom: none; }
.alt-row.empty { opacity: 0.45; }
.alt-row.empty .alt-expr { color: #94a3b8; font-style: italic; }
.alt-row.recommend { background: #f0fdf4; margin: 0 -8px; padding-left: 8px; padding-right: 8px; border-radius: 6px; }
.alt-name { font-size: 12px; font-weight: 600; color: #475569; display: inline-flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.alt-pri {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 9px;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
}
.alt-name em {
  font-style: normal;
  font-size: 11px;
  color: #059669;
  background: #d1fae5;
  padding: 0 5px;
  border-radius: 4px;
}
.alt-rate {
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-align: center;
}
.alt-rate.is-high { color: #059669; }
.alt-rate.is-mid { color: #d97706; }
.alt-rate.is-low { color: #94a3b8; }
.alt-expr {
  font-size: 12px;
  word-break: break-all;
  color: #0f172a;
}

.chain-tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
.chain-table { border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; }
.chain-table-head,
.chain-table-row {
  display: grid;
  grid-template-columns: 56px 110px 64px 1fr 56px 72px;
  gap: 6px;
  align-items: center;
  padding: 8px 10px;
  font-size: 12px;
}
.chain-table-head {
  background: #f8fafc;
  color: #64748b;
  font-weight: 700;
}
.chain-table-row {
  border-top: 1px solid #f1f5f9;
  cursor: pointer;
}
.chain-table-row:hover { background: #f8fafc; }
.chain-table-row.recommend { background: #ecfdf5; }
.chain-table-row.off { opacity: 0.55; }
.col-pri { text-align: center; color: #64748b; }
.col-rate {
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  font-size: 12px;
}
.col-rate.is-high { color: #059669; }
.col-rate.is-mid { color: #d97706; }
.col-rate.is-low { color: #94a3b8; }
.col-name { font-weight: 600; color: #334155; }
.col-name em {
  margin-left: 4px;
  font-style: normal;
  font-size: 11px;
  color: #16a34a;
  font-weight: 700;
}
.col-expr {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
}
.col-enable, .col-sort { display: flex; justify-content: center; }
.chain-footer { margin-top: 10px; }

.relative-cols {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.relative-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}
.relative-col-title {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}
.region-inner { display: flex; gap: 6px; }

.manual-form { max-width: 520px; }

.validate-block { display: flex; flex-direction: column; gap: 6px; }
.validate-meta { font-size: 12px; color: #64748b; }
.validate-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
}
.validate-banner.ok { background: #ecfdf5; color: #166534; }
.validate-banner.fail { background: #fef2f2; color: #b91c1c; }
.validate-detail { display: flex; flex-direction: column; gap: 4px; }
.validate-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #475569;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #cbd5e1;
  flex-shrink: 0;
}
.dot.ok { background: #22c55e; }
.dot.warn { background: #f59e0b; }

.prp-history {
  border-top: 1px dashed #e2e8f0;
  padding-top: 4px;
}
.prp-mode-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.prp-mode-tabs :deep(.el-tabs__item) {
  font-weight: 600;
}
.hist-badge {
  display: inline-block;
  margin-left: 6px;
  min-width: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 700;
}
.prp-history-panel {
  padding: 4px 2px 8px;
}
.history-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.history-panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.history-panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}
.history-panel-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}
.history-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.history-row:hover,
.history-row.active {
  background: #eff6ff;
  border-color: #93c5fd;
}
.hist-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.hist-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.hist-name { font-size: 13px; font-weight: 600; color: #0f172a; }
.hist-meta, .hist-loc { font-size: 11px; color: #64748b; word-break: break-all; }
.history-footer {
  display: flex;
  justify-content: space-between;
  padding-top: 6px;
}

@media (max-width: 1100px) {
  .prp-workspace { grid-template-columns: 1fr; }
  .prp-outline {
    position: static;
    max-height: none;
    flex-direction: row;
    flex-wrap: wrap;
  }
  .relative-cols { grid-template-columns: 1fr; }
  .alt-row { grid-template-columns: 1fr; }
  .chain-table-head,
  .chain-table-row {
    grid-template-columns: 40px 1fr 48px 48px 64px;
  }
  .chain-table-head .col-expr,
  .chain-table-row .col-expr { display: none; }
  .alt-row { grid-template-columns: 1fr auto; }
  .alt-rate { justify-self: end; }
}</style>
