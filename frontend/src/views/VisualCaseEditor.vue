<template>
  <div class="page-container vce-page">
    <PageHeader
      :title="taskId ? '编辑可视化用例' : '手动新增测试用例'"
      subtitle="录入基础信息 → 可视化编排步骤 → 配置断言与策略 → 调试保存"
    >
      <template #actions>
        <div class="header-actions">
          <el-button class="btn-secondary" @click="previewScript">预览脚本</el-button>
          <el-button class="btn-secondary" type="success" plain @click="goAuthorWorkspace">同屏编写</el-button>
          <el-button class="btn-secondary" :disabled="!taskId" @click="openVersionHistory">版本历史</el-button>
          <el-button class="btn-secondary" :disabled="!taskId" @click="openComments">协作批注</el-button>

          <el-dropdown split-button type="warning" :disabled="debugging" @click="debugRun('full')" @command="onDebugCommand">
            {{ debugging ? '调试中…' : '调试执行' }}
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="full">完整调试</el-dropdown-item>
                <el-dropdown-item command="step">单步调试</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown split-button type="primary" :disabled="saving" @click="saveTask('keep')" @command="onSaveCommand">
            {{ saving ? '保存中…' : '保存' }}
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="draft">保存草稿</el-dropdown-item>
                <el-dropdown-item command="active">提交生效</el-dropdown-item>
                <el-dropdown-item command="template">另存为模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>
    </PageHeader>

    <el-row :gutter="16" class="main-row">
      <!-- 模块 2：用例信息 -->
      <el-col :xs="24" :lg="8">
        <AppCard title="用例信息" :hover="false" class="meta-card">
          <el-form :model="meta" label-width="110px" class="meta-form">
            <div class="form-group">
              <div class="form-group-title">基础信息（必配项）</div>

              <el-form-item required>
                <template #label>
                  <span v-tooltip="'用于列表、报告与检索的业务名称'">用例名称</span>
                </template>
                <el-input
                  v-model="meta.name"
                  placeholder="请填写用例名称，如登录流程、支付校验"
                  maxlength="80"
                  show-word-limit
                />
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'将用例归入目录，方便批量管理与筛选'">所属目录</span>
                </template>
                <el-select v-model="meta.folder_id" clearable placeholder="根目录（全部用例）" style="width:100%">
                  <el-option label="根目录（全部用例）" :value="null" />
                  <el-option v-for="f in flatFolders" :key="f.id" :label="f.label" :value="f.id" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'业务模块，用于分类管理与批量筛选'">业务模块</span>
                </template>
                <el-select v-model="meta.module_name" clearable filterable allow-create placeholder="选择或输入模块" style="width:100%">
                  <el-option v-for="m in MODULE_OPTIONS" :key="m" :label="m" :value="m" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'P0 核心链路优先执行，适配定时任务筛选'">优先级</span>
                </template>
                <el-radio-group v-model="meta.priority">
                  <el-radio-button :value="0">P0</el-radio-button>
                  <el-radio-button :value="1">P1</el-radio-button>
                  <el-radio-button :value="2">P2</el-radio-button>
                  <el-radio-button :value="3">P3</el-radio-button>
                </el-radio-group>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'对齐手工用例规范，便于团队阅读'">前置条件</span>
                </template>
                <el-input
                  v-model="meta.preconditions"
                  type="textarea"
                  :rows="2"
                  placeholder="例：已安装 App，账号处于登录态"
                />
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'对齐手工用例规范，描述期望业务结果'">预期结果</span>
                </template>
                <el-input
                  v-model="meta.expected_result"
                  type="textarea"
                  :rows="2"
                  placeholder="例：成功进入首页并展示用户昵称"
                />
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'单次执行最长等待时间，超时后任务将终止'">全局超时 (秒)</span>
                </template>
                <el-input-number v-model="meta.timeout_seconds" :min="60" :max="7200" :step="60" style="width:100%" />
              </el-form-item>
            </div>

            <div class="form-group form-group--advanced">
              <div class="form-group-title">执行策略与高级配置</div>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'步骤失败时整条用例的默认策略（单步可覆盖）'">失败策略</span>
                </template>
                <el-select v-model="meta.on_fail" style="width:100%">
                  <el-option label="失败停止执行" value="fail" />
                  <el-option label="失败继续执行" value="skip" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'调试排障截图策略，平衡排查需求与存储压力'">截图策略</span>
                </template>
                <el-select v-model="meta.screenshot_policy" style="width:100%">
                  <el-option label="仅失败截图" value="on_fail" />
                  <el-option label="每步截图" value="every_step" />
                  <el-option label="不截图" value="off" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'执行全程录制设备画面，生成回放视频'">录屏</span>
                </template>
                <div class="switch-with-hint">
                  <el-switch v-model="meta.enable_recording" />
                  <span class="field-hint">执行全程录制设备画面，生成回放视频</span>
                </div>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'模拟真人操作速率，避免检测自动化脚本'">真人模拟</span>
                </template>
                <div class="switch-with-hint">
                  <el-switch v-model="meta.human_delay" />
                  <span class="field-hint">模拟真人操作速率，避免检测自动化脚本</span>
                </div>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'统一控制步骤间等待节奏：标准 / 快速 / 慢速'">等待模板</span>
                </template>
                <el-select v-model="meta.wait_template" clearable placeholder="标准" style="width:100%">
                  <el-option label="标准" value="standard" />
                  <el-option label="快速" value="smoke" />
                  <el-option label="慢速" value="weak_network" />
                </el-select>
              </el-form-item>

              <el-form-item v-if="isAssetMode">
                <template #label>
                  <span v-tooltip="'草稿不参与定时自动执行；已生效可被套件引用'">用例状态</span>
                </template>
                <el-select v-model="meta.case_status" style="width:100%">
                  <el-option label="草稿" value="draft" />
                  <el-option label="待评审" value="review" />
                  <el-option label="已生效（正式）" value="active" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'逗号分隔标签，便于筛选'">标签</span>
                </template>
                <el-input v-model="meta.tags" placeholder="例：回归,冒烟,登录" />
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'绑定数据集后，执行时可按多组数据循环跑同一用例'">数据驱动</span>
                </template>
                <el-select v-model="meta.dataset_id" clearable filterable placeholder="不绑定数据集" style="width:100%">
                  <el-option
                    v-for="d in datasets"
                    :key="d.id"
                    :label="d.name"
                    :value="d.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item v-if="!isAssetMode" label="账号池互斥">
                <el-switch v-model="meta.use_account_pool" />
              </el-form-item>

              <div v-if="autoSaveHint" class="autosave-hint">{{ autoSaveHint }}</div>
            </div>
          </el-form>
        </AppCard>
      </el-col>

      <!-- 模块 3：右侧工作区 -->
      <el-col :xs="24" :lg="16">
        <AppCard :hover="false" class="workspace-card">
          <el-tabs v-model="rightTab" class="right-tabs">
            <el-tab-pane name="list" label="测试步骤列表">
                          <div class="steps-header">
                            <div class="steps-header-left">
                              <span class="steps-title">测试步骤列表</span>
                              <span class="steps-hint">拖拽步骤调整执行顺序</span>
                            </div>
                            <div class="steps-header-actions">
                              <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                                <span>
                                  <el-button size="small" :disabled="!hasStepSelection" @click="batchIndent(1)">增加缩进</el-button>
                                </span>
                              </el-tooltip>
                              <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                                <span>
                                  <el-button size="small" :disabled="!hasStepSelection" @click="batchIndent(-1)">减少缩进</el-button>
                                </span>
                              </el-tooltip>
                              <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                                <span>
                                  <el-button size="small" :disabled="!hasStepSelection" @click="saveSelectionAsCommon">另存公共步骤</el-button>
                                </span>
                              </el-tooltip>
                              <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                                <span>
                                  <el-button size="small" :disabled="!hasStepSelection" @click="batchSetStepsEnabled(true)">批量启用</el-button>
                                </span>
                              </el-tooltip>
                              <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                                <span>
                                  <el-button size="small" :disabled="!hasStepSelection" @click="batchSetStepsEnabled(false)">批量禁用</el-button>
                                </span>
                              </el-tooltip>
                              <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                                <span>
                                  <el-button size="small" :disabled="!hasStepSelection" @click="batchCopySteps">批量复制</el-button>
                                </span>
                              </el-tooltip>
                              <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                                <span>
                                  <el-button size="small" type="danger" plain :disabled="!hasStepSelection" @click="batchDeleteSteps">批量删除</el-button>
                                </span>
                              </el-tooltip>
                              <el-button size="small" @click="openFindReplace">查找替换</el-button>
                              <el-button size="small" type="danger" plain :disabled="!steps.length" @click="clearAllSteps">清空步骤</el-button>
                              <el-button size="small" type="primary" plain @click="showImportCommon = true">导入公共步骤</el-button>
                              <el-button size="small" type="success" plain @click="goCreateCommonStep">新建公共步骤</el-button>
                              <el-button size="small" @click="showInvokeCase = true">调用用例</el-button>
                              <el-button size="small" @click="addBranchStep">添加分支判断</el-button>
                              <el-button size="small" @click="addLoopStep">添加循环步骤</el-button>
                            </div>
                          </div>

              <div class="steps-body">
                          <div class="steps-toolbar">
                            <el-input
                              v-model="stepKeyword"
                              clearable
                              prefix-icon="Search"
                              placeholder="搜索步骤类型 / 摘要 / 控件"
                              style="max-width:280px"
                            />
                            <span class="steps-count">共 {{ filteredSteps.length }} / {{ steps.length }} 步</span>
                          </div>

                          <div class="steps-scroll">
                            <div v-if="!steps.length" class="steps-empty">
                              <p class="empty-title">暂无测试步骤</p>
                              <div class="empty-actions">
                                <el-button type="primary" @click="focusAddPanel('click')">① 去新增步骤</el-button>
                                <el-button @click="showImportCommon = true">② 从公共步骤库导入</el-button>
                                <el-button @click="rightTab = 'add'; openPoolPicker()">③ 从元素库选择</el-button>
                              </div>
                            </div>

                            <div v-else class="steps-list">
                              <div
                                v-for="item in filteredStepItems"
                                :key="item.step.id"
                                class="step-item"
                                :class="[stepToneClass(item.step.type), { disabled: item.step.enabled === false, selected: selectedStepIds.has(item.step.id), 'is-dragging': dragStepId === item.step.id }]"
                                :style="{ marginLeft: `${(item.depth || 0) * 20}px` }"
                                draggable="true"
                                @dragstart="onStepDragStart($event, item.index)"
                                @dragover.prevent="onStepDragOver($event, item.index)"
                                @drop.prevent="onStepDrop($event, item.index)"
                                @dragend="onStepDragEnd"
                              >
                                <span class="drag-handle" title="拖拽排序">⋮⋮</span>
                                <el-checkbox
                                  :model-value="selectedStepIds.has(item.step.id)"
                                  @change="(v) => toggleStepSelect(item.step.id, v)"
                                />
                                <div class="step-index">{{ item.index + 1 }}</div>
                                <div class="step-body">
                                  <el-switch v-if="item.step.type !== 'end_block'" v-model="item.step.enabled" size="small" />
                                  <el-tag size="small" :type="stepTagType(item.step.type)" effect="plain">
                                    {{ stepTypeLabel(item.step) }}
                                  </el-tag>
                                  <span class="step-desc">{{ stepSummary(item.step) }}</span>
                                  <span v-if="item.step.remark" class="step-remark" :title="item.step.remark">{{ item.step.remark }}</span>
                                  <span v-if="stepLocator(item.step)" class="step-locator">{{ stepLocator(item.step) }}</span>
                                  <el-tag v-if="item.step.enabled === false" size="small" type="info">
                                    {{ item.step.disable_reason || '已禁用' }}
                                  </el-tag>
                                </div>
                                <div class="step-actions">
                                  <el-button v-if="item.step.type !== 'end_block'" size="small" type="primary" plain @click="editStep(item.index)">编辑</el-button>
                                  <el-button size="small" class="btn-copy" @click="copyStep(item.index)">复制</el-button>
                                  <el-button size="small" plain :disabled="item.index === 0" @click="moveStep(item.index, -1)">上移</el-button>
                                  <el-button size="small" plain :disabled="item.index === steps.length - 1" @click="moveStep(item.index, 1)">下移</el-button>
                                  <el-button size="small" type="danger" plain @click="removeStep(item.index)">删除</el-button>
                                </div>
                              </div>
                              <el-empty v-if="!filteredStepItems.length" description="未找到匹配步骤" :image-size="64" />
                            </div>
                          </div>
                        </div>

            </el-tab-pane>
            <el-tab-pane name="add" label="新增步骤">
              <StepAddPanel
                ref="stepAddPanelRef"
                :model-value="newStep"
                :blank-step="newStepDefaults()"
                :catalog-id="selectedCatalogId"
                :expanded-keys="treeExpandedKeys"
                :editing-index="editingIndex"
                :common-steps="commonSteps"
                :locator-hint="locatorHint"
                @update:model-value="onNewStepUpdate"
                @update:catalog-id="(id) => (selectedCatalogId = id)"
                @update:expanded-keys="(keys) => (treeExpandedKeys = keys)"
                @field-change="onStepFieldChange"
                @submit="submitStepForm"
                @cancel="cancelEdit"
                @pick="goElementPicker"
                @pool="openPoolPicker"
                @create-common="goCreateCommonStep"
              />
            </el-tab-pane>
          </el-tabs>
        </AppCard>
      </el-col>
    </el-row>

    <button class="vce-fab-record" type="button" title="一键录制" @click="goQuickRecord">一键录制</button>

    <ScriptPreviewDialog
      v-model="showPreview"
      :code="previewCode"
      :file-name="(meta.name || 'atp_case') + '_script.py'"
    />

    <el-dialog v-model="showImportCommon" title="导入公共步骤" width="520px">
      <el-select v-model="importCommonNames" multiple filterable placeholder="选择要导入的公共步骤" style="width:100%">
        <el-option v-for="s in commonSteps" :key="s.id" :label="s.name" :value="s.name" />
      </el-select>
      <template #footer>
        <el-button @click="showImportCommon = false">取消</el-button>
        <el-button type="primary" @click="confirmImportCommon">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPoolPicker" title="从元素库选择控件" width="680px" destroy-on-close @closed="selectedPoolRows = []">
      <el-input v-model="poolKeyword" clearable placeholder="搜索控件名称 / 页面 / 定位" style="margin-bottom:12px" />
      <el-table
        ref="poolTableRef"
        :data="filteredPoolItems"
        height="360"
        row-key="id"
        @selection-change="onPoolSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="element_name" label="控件名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="page_name" label="页面" width="120" show-overflow-tooltip />
        <el-table-column prop="locator_type" label="定位类型" width="100" />
        <el-table-column prop="locator_value" label="定位表达式" min-width="180" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <div class="pool-picker-footer">
          <span class="pool-pick-count">已选 {{ selectedPoolRows.length }} 项</span>
          <div>
            <el-button plain @click="saveStepToPool" :disabled="!canSaveToPool">另存元素库</el-button>
            <el-button @click="showPoolPicker = false">取消</el-button>
            <el-button type="primary" :disabled="!selectedPoolRows.length" @click="confirmPoolSelection">
              确认选用
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="showFindReplace" title="步骤查找替换" width="480px">
      <el-form label-width="88px">
        <el-form-item label="查找内容" required>
          <el-input v-model="findReplace.find" placeholder="定位符 / 文本 / 变量名" />
        </el-form-item>
        <el-form-item label="替换为">
          <el-input v-model="findReplace.replace" placeholder="替换文本" />
        </el-form-item>
        <el-form-item label="范围">
          <el-checkbox-group v-model="findReplace.fields">
            <el-checkbox label="element_name">控件名</el-checkbox>
            <el-checkbox label="locator_value">定位表达式</el-checkbox>
            <el-checkbox label="text">输入文本</el-checkbox>
            <el-checkbox label="expected">期望文本</el-checkbox>
            <el-checkbox label="remark">备注</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFindReplace = false">取消</el-button>
        <el-button type="primary" @click="applyFindReplace">全部替换</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showVersionDialog" title="用例版本历史" width="720px">
      <el-table :data="versionList" v-loading="versionLoading" max-height="420">
        <el-table-column prop="version_num" label="版本" width="80">
          <template #default="{ row }">v{{ row.version_num }}</template>
        </el-table-column>
        <el-table-column prop="change_note" label="变更说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="created_at" label="时间" width="170">
          <template #default="{ row }">{{ formatVersionTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="rollbackVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showVersionDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showInvokeCase" title="调用其他用例" width="520px">
      <el-select
        v-model="invokeCaseId"
        filterable
        remote
        :remote-method="searchCases"
        :loading="caseSearchLoading"
        placeholder="搜索用例名称"
        style="width:100%"
      >
        <el-option
          v-for="c in caseOptions"
          :key="c.id"
          :label="`${c.name} (#${c.id})`"
          :value="c.id"
          :disabled="String(c.id) === String(taskId)"
        />
      </el-select>
      <p class="form-hint" style="margin-top:10px">将插入「调用其他用例」步骤，执行时展开被调用例的全部步骤</p>
      <template #footer>
        <el-button @click="showInvokeCase = false">取消</el-button>
        <el-button type="primary" :disabled="!invokeCaseId" @click="confirmInvokeCase">插入步骤</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showCommentDialog" title="用例协作批注" width="560px">
      <div class="comment-list">
        <div v-if="!comments.length" class="comment-empty">暂无批注，添加第一条评论开始协作</div>
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-meta">
            <span class="comment-author">{{ c.author_name || c.username || '用户' }}</span>
            <span class="comment-time">{{ formatVersionTime(c.created_at) }}</span>
            <el-button size="small" type="danger" plain @click="deleteComment(c)">删除</el-button>
          </div>
          <div class="comment-body">{{ c.content }}</div>
        </div>
      </div>
      <el-input v-model="newComment" type="textarea" :rows="3" placeholder="输入批注内容..." style="margin-top:12px" />
      <template #footer>
        <el-button @click="showCommentDialog = false">关闭</el-button>
        <el-button type="primary" :disabled="!newComment.trim()" :loading="commentSaving" @click="submitComment">发表批注</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { taskApi, caseApi, commonStepApi, dataFactoryApi, controlApi, datasetApi, commentApi, deviceApi } from '@/api'
import { formatStepTarget, formatStepLocator } from '@/utils/stepDisplay'
import { formatTime } from '@/utils/status'
import ScriptPreviewDialog from '@/components/ScriptPreviewDialog.vue'
import StepAddPanel from '@/components/case-editor/StepAddPanel.vue'
import { getCatalogLeaf, resolveLeafFields, normalizeOnFail } from '@/config/stepCatalog'
import { ElMessage, ElMessageBox } from 'element-plus'

const MODULE_OPTIONS = ['登录', '首页', '商品', '结算', '支付', '个人中心', '消息', '设置', '其他']

const route = useRoute()
const router = useRouter()
const taskId = route.params.id || null
const folderId = route.query.folder_id ? Number(route.query.folder_id) : null
const isAssetMode = route.query.asset === '1' || route.path.startsWith('/cases/')
const saving = ref(false)
const debugging = ref(false)
const showPreview = ref(false)
const previewCode = ref('')
const commonSteps = ref([])
const dataFactoryTemplates = ref([])
const datasets = ref([])
const poolItems = ref([])
const flatFolders = ref([])
const stepKeyword = ref('')
const selectedStepIds = ref(new Set())
const showImportCommon = ref(false)
const importCommonNames = ref([])
const showPoolPicker = ref(false)
const poolKeyword = ref('')
const selectedPoolRows = ref([])
const poolTableRef = ref(null)
const showFindReplace = ref(false)
const findReplace = reactive({
  find: '',
  replace: '',
  fields: ['element_name', 'locator_value', 'text', 'expected']
})
const showVersionDialog = ref(false)
const versionLoading = ref(false)
const versionList = ref([])
const showInvokeCase = ref(false)
const invokeCaseId = ref(null)
const caseOptions = ref([])
const caseSearchLoading = ref(false)
const showCommentDialog = ref(false)
const comments = ref([])
const newComment = ref('')
const commentSaving = ref(false)
const dragFromIndex = ref(-1)
const dragStepId = ref(null)
const editingIndex = ref(null)
const rightTab = ref('list')
const selectedCatalogId = ref('ctrl.android.click')
const treeExpandedKeys = ref(['ctrl', 'ctrl.android', 'ctrl.ios', 'ctrl.webview'])
const stepAddPanelRef = ref(null)
const autoSaveHint = ref('')
const locatorHint = ref('')
let stepSeq = 1
let autoSaveTimer = null
let dirty = false
let lastSavedSnapshot = ''

const meta = reactive({
  name: '',
  platform: 'android',
  app_package: '',
  module_name: '',
  priority: 1,
  preconditions: '',
  expected_result: '',
  tags: '',
  dataset_id: null,
  timeout_seconds: 3600,
  enable_recording: true,
  human_delay: false,
  wait_template: 'standard',
  on_fail: 'fail',
  screenshot_policy: 'on_fail',
  use_account_pool: false,
  case_status: 'draft',
  folder_id: folderId || null
})

const steps = ref([])

const newStepDefaults = () => ({
  type: 'wait',
  seconds: 2,
  wait_mode: 'fixed',
  wait_timeout: 10,
  element_name: '',
  display_name: '',
  locator_type: '',
  locator_value: '',
  remark: '',
  text: '',
  app_package: '',
  expected: '',
  actual: '',
  op: 'contains',
  expected_count: 1,
  attr_name: '',
  var_name: '',
  catalog_id: '',
  platform: '',
  context: '',
  x: 0, y: 0,
  x1: 500, y1: 800, x2: 500, y2: 400,
  duration_ms: 300,
  common_step: '', input_params_json: '', check_types: 'all', mode: 'disk',
  case_id: null, case_name: '',
  method: 'GET', url: '', expected_status: 200, body_contains: '', timeout: 5,
  event_name: '', props_json: '{}', verify_url: '',
  conditions: '[{"type":"text","value":"首页"}]',
  enabled: true, disable_reason: '', disable_mode: '',
  retry_count: 0, on_fail: 'fail', key: 'back', template_id: null, template_path: '', threshold: 0.85,
  prompt: '', profile: '2g', locale: 'zh_cn', max_ms: 5000,
  stream: 'music', tolerance: 0, direction: 'up',
  condition: '', branch_true: '', branch_false: '', loop_count: 3, loop_body: '',
  offset_minutes: 5, confirm: false,
  script_lang: 'python', script_code: '', script_timeout: 120,
  save_path: '', handle: '', strategy: '', event_count: 50, throttle_ms: 200,
  interval_ms: 0, touch_mode: 'default', firmware_path: '', command: '', key_code: '', distance: 300
})

const newStep = reactive(newStepDefaults())

const customScriptPlaceholder = computed(() => {
  if (newStep.script_lang === 'java') {
    return `// 方式一：只写逻辑（自动包装为 AtpCustomScript.main）
System.out.println("device=" + System.getenv("ATP_DEVICE_SERIAL"));

// 方式二：完整 public class Xxx { public static void main(...) { ... } }`
  }
  return `# 可用变量：serial, var(name), set_var(name, value), os, subprocess
print("device=", serial)
print("demo var=", var("TIME_HM", ""))`
})

const hasStepSelection = computed(() => selectedStepIds.value.size > 0)

const canSaveToPool = computed(() => {
  const name = (newStep.element_name || '').trim()
  const loc = (newStep.locator_value || '').trim()
  return !!(name && (loc || newStep.locator_type))
})

const filteredPoolItems = computed(() => {
  const k = poolKeyword.value.trim().toLowerCase()
  if (!k) return poolItems.value
  return poolItems.value.filter(p => {
    const hay = [p.element_name, p.page_name, p.locator_type, p.locator_value, p.device_element_value]
      .join(' ').toLowerCase()
    return hay.includes(k)
  })
})

const filteredSteps = computed(() => {
  const k = stepKeyword.value.trim().toLowerCase()
  if (!k) return steps.value
  return steps.value.filter(s => {
    const hay = [
      stepTypeLabel(s),
      stepSummary(s),
      stepLocator(s),
      s.element_name,
      s.expected,
      s.common_step,
      s.condition,
      s.remark,
      s.locator_value
    ].join(' ').toLowerCase()
    return hay.includes(k)
  })
})

const filteredStepItems = computed(() => {
  const depths = computeStepDepths(steps.value)
  return filteredSteps.value.map(step => {
    const index = steps.value.findIndex(s => s.id === step.id)
    return {
      step,
      index,
      depth: index >= 0 ? depths[index] : 0
    }
  }).filter(x => x.index >= 0)
})

function computeStepDepths(list) {
  const depths = []
  let depth = 0
  for (const s of list) {
    if (s.type === 'end_block') {
      depth = Math.max(0, depth - 1)
      depths.push(typeof s.indent === 'number' ? s.indent : depth)
      continue
    }
    const d = typeof s.indent === 'number' ? s.indent : depth
    depths.push(d)
    if (s.type === 'branch' || s.type === 'loop') {
      depth = Math.min(6, (typeof s.indent === 'number' ? s.indent : depth) + 1)
    }
  }
  return depths
}

const typeLabels = {
  wait: '等待', click: '点击', tap_xy: '坐标点击', input: '输入', clear_input: '清空输入', launch: '启动', swipe: '滑动',
  assert_text: '断言', assert_exists: '断言存在', assert_not_exists: '断言不存在', invoke_common: '公共步骤',
  invoke_case: '调用用例',
  check_anomaly: '异常检测', assert_process: '进程校验', clear_cache: '清理缓存', force_stop: '强制停止',
  assert_toast: 'Toast断言', assert_http: 'HTTP断言', assert_analytics: '埋点校验', assert_composite: '组合断言', dismiss_popup: '关弹窗',
  switch_context: '切换上下文', revoke_permissions: '权限回收',
  assert_ocr: 'OCR断言', tap_ocr: 'OCR点击',
  press_key: '系统按键', clipboard_set: '剪贴板', clipboard_assert: '剪贴板断言',
  wake_screen: '亮屏', lock_screen: '锁屏', assert_screen: '屏幕断言', assert_key: '按键断言',
  assert_volume: '音量断言', assert_volume_change: '音量变更断言',
  assert_image: '图像断言', data_factory: '动态造数', use_account_pool: '账号池',
  manual_wait: '人工介入', network_profile: '弱网模拟', reset_network: '恢复网络', capture_crash: '崩溃捕获',
  set_locale: '切换语言', collect_performance: '性能采集', assert_cold_start: '冷启动',
  install_apk: '安装包', branch: '分支判断', loop: '循环', end_block: '结束块',
  set_relative_time: '设置相对时间',
  custom_script: '自定义脚本',
  screenshot: '获取截图',
  rotate_screen: '旋转屏幕',
  set_auto_rotate: '自动旋转设置',
  swipe_from_center: '中央滑动',
  uninstall_app: '卸载应用',
  assert_compare: '断言验证',
  assert_element_count: '断言个数',
  assert_attribute: '断言属性',
  get_text: '获取文本',
  log_element: '控件日志',
  drag_element: '拖拽控件',
  scroll_to_element: '滚动到控件',
  set_find_strategy: '查找策略',
  switch_handle: '切换 Handle',
  long_press: '长按',
  random_event: '随机事件',
  set_step_interval: '步骤间隔',
  set_touch_mode: '触控模式',
  robot_firmware_upgrade: '固件升级',
  robot_log_assert: '机器人日志断言',
  robot_send_command: '机器人命令'
}

const WAIT_TYPES = new Set(['wait', 'manual_wait'])
const CLICK_TYPES = new Set(['click', 'tap_xy', 'long_press', 'tap_ocr', 'input', 'clear_input', 'swipe', 'set_relative_time'])
const ASSERT_TYPES = new Set([
  'assert_text', 'assert_exists', 'assert_not_exists', 'assert_ocr', 'assert_toast', 'assert_http', 'assert_analytics',
  'assert_composite', 'check_anomaly', 'assert_process', 'clipboard_assert', 'assert_screen',
  'assert_key', 'assert_volume', 'assert_volume_change', 'assert_image', 'assert_cold_start',
  'assert_compare', 'assert_element_count', 'assert_attribute'
])
const APP_TYPES = new Set([
  'launch', 'clear_cache', 'force_stop', 'install_apk', 'switch_context', 'revoke_permissions',
  'press_key', 'clipboard_set', 'wake_screen', 'lock_screen', 'dismiss_popup', 'custom_script',
  'invoke_common', 'invoke_case', 'branch', 'loop', 'end_block'
])

function stepTypeLabel(stepOrType, step) {
  const s = typeof stepOrType === 'object' ? stepOrType : step
  const t = typeof stepOrType === 'object' ? stepOrType?.type : stepOrType
  const base = typeLabels[t] || t
  if (!s) return base
  if (s.platform === 'ios') return `iOS·${base}`
  if (s.context === 'webview') return `Web·${base}`
  if (s.context === 'poco') return `Poco·${base}`
  if (t === 'assert_compare') {
    const opMap = {
      eq: '相等', equals: '相等',
      ne: '不相等', not_equals: '不相等',
      contains: '包含', not_contains: '不包含'
    }
    return `断言验证（${opMap[s.op] || s.op || '包含'}）`
  }
  return base
}

function stepToneClass(type) {
  if (WAIT_TYPES.has(type)) return 'tone-wait'
  if (CLICK_TYPES.has(type)) return 'tone-click'
  if (ASSERT_TYPES.has(type)) return 'tone-assert'
  if (APP_TYPES.has(type) || type === 'branch' || type === 'loop') return 'tone-app'
  return 'tone-default'
}

function stepTagType(type) {
  if (WAIT_TYPES.has(type)) return 'info'
  if (CLICK_TYPES.has(type)) return 'primary'
  if (ASSERT_TYPES.has(type)) return 'success'
  if (APP_TYPES.has(type)) return 'warning'
  if (type === 'branch' || type === 'loop') return 'warning'
  return ''
}

function stepLocator(step) {
  return formatStepLocator(step)
}

function stepSummary(step) {
  const target = formatStepTarget(step)
  switch (step.type) {
    case 'wait': {
      if (step.wait_mode === 'appear') return `等待出现 ${step.element_name || ''} (${step.seconds || 10}s)`
      if (step.wait_mode === 'disappear') return `等待消失 ${step.element_name || ''} (${step.seconds || 10}s)`
      return `${step.seconds}s`
    }
    case 'click':
    case 'tap_xy':
    case 'long_press':
    case 'tap_ocr':
      return target || stepTypeLabel(step.type)
    case 'input': return step.text
    case 'clear_input': return step.element_name || '清空当前输入框'
    case 'assert_exists': return step.element_name || '控件存在'
    case 'assert_not_exists': return step.element_name || '控件不存在'
    case 'assert_element_count': return `${step.element_name || '控件'} × ${step.expected_count ?? '?'}`
    case 'assert_compare': return `${step.actual || ''} ${step.op || 'contains'} ${step.expected || ''}`
    case 'assert_attribute': return `${step.element_name || ''} · ${step.attr_name || 'attr'}=${step.expected || ''}`
    case 'get_text': return `${step.element_name || ''} → $${step.var_name || 'VAR'}`
    case 'screenshot': return step.save_path || '默认任务目录'
    case 'set_relative_time': {
      const mins = step.offset_minutes ?? 5
      return `+${mins}分钟 → TIME_HM${step.confirm ? ' · 并点确定' : ''}`
    }
    case 'launch': return step.app_package || meta.app_package
    case 'install_apk': return step.app_package || '安装包'
    case 'swipe': return target || `${step.x1},${step.y1}→${step.x2},${step.y2}`
    case 'assert_text': return `${step.element_name || ''} = ${step.expected}`
    case 'invoke_common': return step.common_step || '(未选)'
    case 'invoke_case': return step.case_name || (step.case_id ? `#${step.case_id}` : '(未选)')
    case 'custom_script': {
      const lang = (step.script_lang || 'python').toUpperCase()
      const name = step.element_name || step.display_name || ''
      const lines = (step.script_code || '').split('\n').filter(Boolean).length
      return name ? `${lang} · ${name}` : `${lang} · ${lines} 行`
    }
    case 'check_anomaly': return step.check_types || 'all'
    case 'assert_process': return meta.app_package || '当前应用'
    case 'clear_cache': return { disk: '磁盘缓存', memory: '内存', all: '全部' }[step.mode] || step.mode
    case 'force_stop': return meta.app_package || '当前应用'
    case 'assert_toast': return step.expected
    case 'assert_http': return `${step.method || 'GET'} ${step.url}`
    case 'assert_analytics': return step.event_name || '(未填事件)'
    case 'assert_composite': return '多条件'
    case 'dismiss_popup': return '系统弹窗'
    case 'switch_context': return { auto: '自动', native: 'Native', webview: 'WebView' }[step.mode] || step.mode
    case 'revoke_permissions': return 'pm revoke'
    case 'assert_ocr': return step.expected
    case 'press_key': case 'assert_key': return step.key || 'back'
    case 'clipboard_set': return step.text
    case 'clipboard_assert': return step.expected
    case 'wake_screen': return '唤醒'
    case 'lock_screen': return '锁屏'
    case 'assert_screen': return step.expected || 'on'
    case 'assert_volume': return `${step.stream || 'music'}=${step.expected ?? 0}`
    case 'assert_volume_change': return `${step.direction || 'up'} ${step.key || 'auto'}`
    case 'assert_image': return `${step.template_path} ≥${step.threshold || 0.85}`
    case 'data_factory': return dataFactoryTemplates.value.find(t => t.id === step.template_id)?.name || `#${step.template_id}`
    case 'manual_wait': return step.prompt || '等待人工处理'
    case 'network_profile': return step.profile || '2g'
    case 'reset_network': return '恢复正常'
    case 'capture_crash': return '抓取崩溃日志'
    case 'set_locale': return step.locale || 'zh_cn'
    case 'collect_performance': return '内存/性能'
    case 'assert_cold_start': return `≤${step.max_ms || 5000}ms`
    case 'branch': return step.condition || '条件分支'
    case 'loop': return `循环 ${step.loop_count || 1} 次`
    case 'end_block': return step.block_type === 'loop' ? '结束循环' : '结束分支'
    default: return target || step.element_name || step.expected || stepTypeLabel(step.type)
  }
}

function flattenFolders(nodes, prefix = '') {
  const list = []
  for (const node of nodes || []) {
    if (!node.id) continue
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    list.push({ id: node.id, label })
    if (node.children?.length) list.push(...flattenFolders(node.children, label))
  }
  return list
}

async function loadFolders() {
  try {
    const res = await caseApi.folderTree()
    flatFolders.value = flattenFolders(res.data || [])
  } catch {
    flatFolders.value = []
  }
}

function resetNewStep(type = 'wait') {
  const next = { ...newStepDefaults(), type }
  Object.keys(newStep).forEach((k) => {
    if (!(k in next)) delete newStep[k]
  })
  Object.assign(newStep, next)
}

function onNewStepUpdate(payload) {
  if (!payload || typeof payload !== 'object') return
  // 先清掉旧键再合并，避免切换指令后残留无关字段
  Object.keys(newStep).forEach((k) => {
    if (!(k in payload)) delete newStep[k]
  })
  Object.assign(newStep, payload)
  onLocatorFieldChange()
}

function onStepFieldChange(key) {
  if (['locator_type', 'locator_value', 'element_name'].includes(key)) {
    onLocatorFieldChange()
  }
}

function toggleStepSelect(id, checked) {
  const next = new Set(selectedStepIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedStepIds.value = next
}

function quickAddStep(type, extra = {}) {
  const step = {
    id: stepSeq++,
    enabled: true,
    disable_reason: '',
    disable_mode: '',
    ...newStepDefaults(),
    type,
    ...extra
  }
  steps.value.push(step)
  ElMessage.success(`已添加「${stepTypeLabel(type)}」`)
}

function quickAddCustomScript() {
  quickAddStep('custom_script', {
    script_lang: 'python',
    script_code: 'print("hello from custom script", serial)',
    element_name: '自定义脚本',
    script_timeout: 120
  })
}


const LAST_RECORD_DEVICE_KEY = 'atp_last_record_device_id'

const TYPE_TO_CATALOG = {
  'assert_attribute': 'ctrl.android.verify_attr',
  'assert_compare': 'verify.contains',
  'assert_element_count': 'ctrl.android.count',
  'assert_exists': 'ctrl.android.exists',
  'assert_text': 'ctrl.android.assert_text',
  'clear_cache': 'app.basic.clear_cache',
  'clear_input': 'ctrl.webview.clear',
  'click': 'ctrl.android.click',
  'custom_script': 'script.custom',
  'drag_element': 'ctrl.android.drag',
  'force_stop': 'app.basic.stop',
  'get_text': 'ctrl.android.get_text',
  'input': 'ctrl.android.input',
  'install_apk': 'app.basic.install',
  'invoke_common': 'flow.common',
  'launch': 'app.basic.launch',
  'lock_screen': 'device.screen.lock',
  'log_element': 'ctrl.android.log_info',
  'long_press': 'ctrl.android.long_press',
  'press_key': 'device.special.key',
  'random_event': 'script.monkey',
  'robot_firmware_upgrade': 'robot.firmware',
  'robot_log_assert': 'robot.log_assert',
  'robot_send_command': 'robot.command',
  'rotate_screen': 'device.screen.rotate_left',
  'screenshot': 'image.screenshot',
  'scroll_to_element': 'ctrl.android.scroll_to',
  'set_auto_rotate': 'device.screen.auto_rotate_off',
  'set_find_strategy': 'ctrl.android.find_strategy',
  'set_step_interval': 'runtime.step_interval',
  'set_touch_mode': 'runtime.touch_mode',
  'swipe': 'ctrl.coord.swipe',
  'swipe_from_center': 'device.screen.swipe_center',
  'switch_context': 'ctrl.android.switch_window',
  'switch_handle': 'ctrl.webview.handle',
  'tap_xy': 'ctrl.coord.tap',
  'uninstall_app': 'app.basic.uninstall',
  'wait': 'flow.wait',
  'wake_screen': 'device.screen.unlock'
}

const IOS_TYPE_TO_CATALOG = {
  assert_exists: 'ctrl.ios.exists',
  assert_element_count: 'ctrl.ios.count',
  click: 'ctrl.ios.click',
  input: 'ctrl.ios.input',
  clear_input: 'ctrl.ios.clear',
  long_press: 'ctrl.ios.long_press',
  scroll_to_element: 'ctrl.ios.scroll_to',
  assert_text: 'ctrl.ios.assert_text',
  get_text: 'ctrl.ios.get_text',
  set_find_strategy: 'ctrl.ios.find_strategy'
}

const WEBVIEW_TYPE_TO_CATALOG = {
  assert_exists: 'ctrl.webview.exists',
  assert_element_count: 'ctrl.webview.count',
  click: 'ctrl.webview.click',
  input: 'ctrl.webview.input',
  clear_input: 'ctrl.webview.clear',
  scroll_to_element: 'ctrl.webview.scroll_top',
  set_find_strategy: 'ctrl.webview.find_strategy',
  switch_context: 'ctrl.webview.switch'
}

function resolveCatalogId(step) {
  if (!step) return ''
  if (step.catalog_id && getCatalogLeaf(step.catalog_id)) return step.catalog_id
  if (step.platform === 'ios' && IOS_TYPE_TO_CATALOG[step.type]) {
    return IOS_TYPE_TO_CATALOG[step.type]
  }
  if (step.context === 'webview' && WEBVIEW_TYPE_TO_CATALOG[step.type]) {
    return WEBVIEW_TYPE_TO_CATALOG[step.type]
  }
  if (step.context === 'poco') {
    const pocoMap = {
      assert_exists: 'ctrl.poco.exists',
      assert_element_count: 'ctrl.poco.count',
      click: 'ctrl.poco.click',
      input: 'ctrl.poco.input',
      drag_element: 'ctrl.poco.drag',
      set_find_strategy: 'ctrl.poco.find_strategy'
    }
    if (pocoMap[step.type]) return pocoMap[step.type]
  }
  if (step.type === 'assert_compare') {
    const opMap = {
      eq: 'verify.equals',
      equals: 'verify.equals',
      ne: 'verify.not_equals',
      not_equals: 'verify.not_equals',
      contains: 'verify.contains',
      not_contains: 'verify.not_contains'
    }
    return opMap[step.op] || 'verify.contains'
  }
  if (step.type === 'rotate_screen') {
    return step.direction === 'right' ? 'device.screen.rotate_right' : 'device.screen.rotate_left'
  }
  return TYPE_TO_CATALOG[step.type] || ''
}

function buildStepFromForm() {
  const leaf = getCatalogLeaf(selectedCatalogId.value)
  const step = {
    id: stepSeq++,
    enabled: true,
    disable_reason: '',
    disable_mode: '',
    ...JSON.parse(JSON.stringify(newStep)),
    ...(leaf?.extras || {}),
    catalog_id: selectedCatalogId.value || newStep.catalog_id || ''
  }
  if (step.type === 'invoke_common' && step.input_params_json) {
    try {
      step.input_params = JSON.parse(step.input_params_json)
    } catch {
      ElMessage.warning('入参 JSON 格式错误')
      return null
    }
  }
  delete step.input_params_json
  if (step.type === 'clear_cache' && !step.mode) step.mode = 'disk'
  step.on_fail = normalizeOnFail(step.on_fail)
  // 控件名变更时同步展示名，保证步骤列表与脚本埋点标签一致
  const name = (step.element_name || '').trim()
  if (name) {
    step.element_name = name
    step.display_name = name
  }
  return step
}

function validateStepForm() {
  const leaf = getCatalogLeaf(selectedCatalogId.value)
  if (!leaf) {
    ElMessage.warning('请先选择指令')
    return false
  }
  const fields = resolveLeafFields(leaf)
  if (fields.includes('element_name') && !(newStep.element_name || '').trim()) {
    ElMessage.warning('请填写控件名')
    return false
  }
  if (fields.includes('locator_value') && !(newStep.locator_value || '').trim()) {
    ElMessage.warning('请填写定位表达式')
    return false
  }
  if (fields.includes('text') && newStep.type === 'input' && newStep.text == null) {
    ElMessage.warning('请填写输入文本')
    return false
  }
  if (fields.includes('common_step') && !(newStep.common_step || '').trim()) {
    ElMessage.warning('请选择公共步骤')
    return false
  }
  if (fields.includes('script_code') && !(newStep.script_code || '').trim()) {
    ElMessage.warning('请填写脚本代码')
    return false
  }
  if (locatorHint.value) {
    ElMessage.warning(locatorHint.value)
    return false
  }
  return true
}

function submitStepForm() {
  onLocatorFieldChange()
  if (!validateStepForm()) return
  if (editingIndex.value !== null) {
    const step = buildStepFromForm()
    if (!step) return
    const id = steps.value[editingIndex.value]?.id
    step.id = id ?? step.id
    steps.value[editingIndex.value] = step
    editingIndex.value = null
    resetNewStep()
    ElMessage.success('步骤已更新')
    rightTab.value = 'list'
    return
  }
  addStep()
  rightTab.value = 'list'
}

function addStep() {
  const step = buildStepFromForm()
  if (!step) return
  steps.value.push(step)
  ElMessage.success('步骤已添加')
  resetNewStep(step.type)
  if (selectedCatalogId.value) {
    // 保持当前指令，清空业务字段便于连续添加
    const leaf = getCatalogLeaf(selectedCatalogId.value)
    if (leaf && stepAddPanelRef.value?.applyLeaf) {
      stepAddPanelRef.value.applyLeaf(leaf, { keepCurrent: false })
    }
  }
}

function editStep(idx) {
  const step = steps.value[idx]
  if (!step) return
  editingIndex.value = idx
  Object.assign(newStep, newStepDefaults(), JSON.parse(JSON.stringify(step)), {
    input_params_json: step.input_params ? JSON.stringify(step.input_params, null, 2) : (step.input_params_json || '')
  })
  const catalogId = resolveCatalogId(step)
  if (catalogId) selectedCatalogId.value = catalogId
  rightTab.value = 'add'
  nextTick(() => {
    document.querySelector('.workspace-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function cancelEdit() {
  editingIndex.value = null
  resetNewStep()
}

function copyStep(idx) {
  const src = steps.value[idx]
  if (!src) return
  const cloned = { ...JSON.parse(JSON.stringify(src)), id: stepSeq++ }
  steps.value.splice(idx + 1, 0, cloned)
  ElMessage.success('步骤已复制')
}

function batchSetStepsEnabled(enabled) {
  const ids = selectedStepIds.value
  let n = 0
  for (const s of steps.value) {
    if (!ids.has(s.id)) continue
    s.enabled = enabled
    if (!enabled && !s.disable_reason) s.disable_reason = '批量禁用'
    if (enabled) s.disable_reason = ''
    n += 1
  }
  selectedStepIds.value = new Set()
  ElMessage.success(enabled ? `已启用 ${n} 个步骤` : `已禁用 ${n} 个步骤`)
}

function openFindReplace() {
  findReplace.find = ''
  findReplace.replace = ''
  showFindReplace.value = true
}

function applyFindReplace() {
  const needle = findReplace.find
  if (!needle) {
    ElMessage.warning('请填写查找内容')
    return
  }
  const fields = findReplace.fields?.length
    ? findReplace.fields
    : ['element_name', 'locator_value', 'text', 'expected', 'remark']
  let count = 0
  for (const s of steps.value) {
    for (const f of fields) {
      const cur = s[f]
      if (typeof cur !== 'string' || !cur.includes(needle)) continue
      s[f] = cur.split(needle).join(findReplace.replace ?? '')
      count += 1
    }
  }
  showFindReplace.value = false
  ElMessage.success(count ? `已替换 ${count} 处` : '未找到匹配内容')
}

async function openPoolPicker() {
  showPoolPicker.value = true
  poolKeyword.value = ''
  selectedPoolRows.value = []
  if (poolItems.value.length) return
  try {
    const res = await controlApi.listPool({ page: 1, page_size: 500, status: 'active' })
    poolItems.value = res.data?.list || res.data?.items || res.data || []
  } catch {
    poolItems.value = []
    ElMessage.warning('加载元素库失败')
  }
}

function onPoolSelectionChange(rows) {
  selectedPoolRows.value = rows || []
}

function fillStepFromPool(row) {
  if (!row) return
  newStep.element_name = row.element_name || ''
  newStep.display_name = row.element_name || ''
  newStep.locator_type = row.locator_type || ''
  newStep.locator_value = row.locator_value || ''
  newStep.pool_id = row.id
  if (row.page_name) {
    newStep.remark = `来自元素库 · ${row.page_name}`
  }
  onLocatorFieldChange()
}

function buildClickStepFromPool(row) {
  return {
    id: stepSeq++,
    enabled: true,
    disable_reason: '',
    disable_mode: '',
    ...newStepDefaults(),
    type: 'click',
    element_name: row.element_name || '',
    display_name: row.element_name || '',
    locator_type: row.locator_type || '',
    locator_value: row.locator_value || '',
    pool_id: row.id,
    remark: row.page_name ? `来自元素库 · ${row.page_name}` : ''
  }
}

function confirmPoolSelection() {
  const rows = selectedPoolRows.value
  if (!rows.length) {
    ElMessage.warning('请先勾选控件')
    return
  }
  if (rows.length === 1) {
    // 单选：回填到当前步骤表单，便于继续编辑动作类型
    if (!['click', 'input', 'clear_input', 'assert_text', 'assert_exists', 'assert_not_exists', 'assert_ocr', 'tap_ocr', 'wait'].includes(newStep.type)) {
      newStep.type = 'click'
    }
    fillStepFromPool(rows[0])
    showPoolPicker.value = false
    ElMessage.success(`已选用控件「${rows[0].element_name}」`)
    return
  }
  // 多选：批量插入点击步骤
  const added = rows.map(buildClickStepFromPool)
  steps.value.push(...added)
  fillStepFromPool(rows[rows.length - 1])
  showPoolPicker.value = false
  ElMessage.success(`已添加 ${added.length} 个控件步骤`)
}

function onLocatorFieldChange() {
  locatorHint.value = ''
  const type = newStep.locator_type
  const v = String(newStep.locator_value || '').trim()
  if (!type || !v) return
  if (type === 'id' && (v.startsWith('//') || v.startsWith('/hierarchy') || v.startsWith('xpath='))) {
    locatorHint.value = '资源 ID 表达式异常：请勿填写 XPath 路径'
  } else if (type === 'xpath' && !(v.startsWith('/') || v.startsWith('(') || v.startsWith('./') || v.startsWith('.//'))) {
    locatorHint.value = 'XPath 通常以 /、// 或 ( 开头，请检查表达式'
  } else if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(v)) {
    locatorHint.value = '定位表达式包含非法控制字符'
  }
}

async function saveStepToPool() {
  if (!canSaveToPool.value) {
    ElMessage.warning('请先填写控件名与定位信息')
    return
  }
  try {
    await controlApi.createPool({
      element_name: newStep.element_name.trim(),
      page_name: meta.module_name || '',
      platform: meta.platform || 'android',
      locator_type: newStep.locator_type || 'id',
      locator_value: (newStep.locator_value || '').trim(),
      device_element_value: newStep.element_name.trim(),
      control_tag: 'static',
      isCore: false
    })
    ElMessage.success('已保存到元素库')
    poolItems.value = []
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存元素库失败')
  }
}

function batchCopySteps() {
  const ids = selectedStepIds.value
  const copies = steps.value
    .filter(s => ids.has(s.id))
    .map(s => ({ ...JSON.parse(JSON.stringify(s)), id: stepSeq++ }))
  steps.value.push(...copies)
  selectedStepIds.value = new Set()
  ElMessage.success(`已复制 ${copies.length} 个步骤`)
}

async function batchDeleteSteps() {
  const n = selectedStepIds.value.size
  await ElMessageBox.confirm(`确定删除选中的 ${n} 个步骤？`, '批量删除', { type: 'warning' })
  steps.value = steps.value.filter(s => !selectedStepIds.value.has(s.id))
  selectedStepIds.value = new Set()
  ElMessage.success('已删除')
}

async function removeStep(idx) {
  try {
    await ElMessageBox.confirm('确定删除该步骤？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  const id = steps.value[idx]?.id
  steps.value.splice(idx, 1)
  if (id != null) {
    const next = new Set(selectedStepIds.value)
    next.delete(id)
    selectedStepIds.value = next
  }
  if (editingIndex.value === idx) cancelEdit()
  else if (editingIndex.value !== null && editingIndex.value > idx) editingIndex.value -= 1
}

function moveStep(idx, delta) {
  const target = idx + delta
  if (target < 0 || target >= steps.value.length) return
  const arr = steps.value
  ;[arr[idx], arr[target]] = [arr[target], arr[idx]]
  if (editingIndex.value === idx) editingIndex.value = target
  else if (editingIndex.value === target) editingIndex.value = idx
}

function addBranchStep() {
  const depths = computeStepDepths(steps.value)
  const base = depths.length ? depths[depths.length - 1] : 0
  quickAddStep('branch', { condition: '控件存在', branch_true: '执行成立分支', branch_false: '执行否则分支', indent: base })
  quickAddStep('end_block', { block_type: 'branch', remark: '结束分支', indent: base })
  ElMessage.info('已插入分支块：请在「分支判断」与「结束块」之间添加子步骤，可勾选后点「增加缩进」')
}

function addLoopStep() {
  const depths = computeStepDepths(steps.value)
  const base = depths.length ? depths[depths.length - 1] : 0
  quickAddStep('loop', { loop_count: 3, loop_body: '循环体步骤', indent: base })
  quickAddStep('end_block', { block_type: 'loop', remark: '结束循环', indent: base })
  ElMessage.info('已插入循环块：请在「循环」与「结束块」之间添加子步骤，可勾选后点「增加缩进」')
}

function batchIndent(delta) {
  const ids = selectedStepIds.value
  let n = 0
  for (const s of steps.value) {
    if (!ids.has(s.id)) continue
    const cur = typeof s.indent === 'number' ? s.indent : 0
    s.indent = Math.max(0, Math.min(6, cur + delta))
    n += 1
  }
  ElMessage.success(delta > 0 ? `已增加 ${n} 步缩进` : `已减少 ${n} 步缩进`)
}

async function saveSelectionAsCommon() {
  const ids = selectedStepIds.value
  const selected = steps.value.filter(s => ids.has(s.id) && s.type !== 'end_block')
  if (!selected.length) {
    ElMessage.warning('请勾选要另存的步骤')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入公共步骤名称', '另存为公共步骤', {
    inputValue: `${meta.name || '未命名'}_公共步骤`,
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (!value?.trim()) return
  try {
    const payloadSteps = selected.map(({ id, ...rest }) => rest)
    await commonStepApi.create({
      name: value.trim(),
      status: 'active',
      steps_content: JSON.stringify({ version: 1, steps: payloadSteps })
    })
    ElMessage.success('已保存到公共步骤库')
    try { commonSteps.value = (await commonStepApi.list()).data || [] } catch { /* ignore */ }
    selectedStepIds.value = new Set()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存公共步骤失败')
  }
}

async function openComments() {
  if (!taskId) {
    ElMessage.warning('请先保存用例后再添加批注')
    return
  }
  showCommentDialog.value = true
  newComment.value = ''
  try {
    const res = await commentApi.list('test_case', taskId)
    comments.value = res.data || []
  } catch {
    comments.value = []
  }
}

async function submitComment() {
  if (!newComment.value.trim() || !taskId) return
  commentSaving.value = true
  try {
    await commentApi.create({
      asset_type: 'test_case',
      asset_id: Number(taskId),
      content: newComment.value.trim()
    })
    newComment.value = ''
    const res = await commentApi.list('test_case', taskId)
    comments.value = res.data || []
    ElMessage.success('批注已发表')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '发表失败')
  } finally {
    commentSaving.value = false
  }
}

async function deleteComment(c) {
  try {
    await ElMessageBox.confirm('确定删除该批注？', '确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await commentApi.delete(c.id)
    comments.value = comments.value.filter(x => x.id !== c.id)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

function onStepDragStart(e, index) {
  dragFromIndex.value = index
  dragStepId.value = steps.value[index]?.id ?? null
  try { e.dataTransfer.setData('text/plain', String(index)) } catch { /* ignore */ }
  e.dataTransfer.effectAllowed = 'move'
}

function onStepDragOver(e, index) {
  e.dataTransfer.dropEffect = 'move'
}

function onStepDrop(e, toIndex) {
  const from = dragFromIndex.value
  if (from < 0 || from === toIndex) return
  const arr = [...steps.value]
  const [moved] = arr.splice(from, 1)
  arr.splice(toIndex, 0, moved)
  steps.value = arr
  if (editingIndex.value === from) editingIndex.value = toIndex
  else if (editingIndex.value !== null) {
    if (from < editingIndex.value && toIndex >= editingIndex.value) editingIndex.value -= 1
    else if (from > editingIndex.value && toIndex <= editingIndex.value) editingIndex.value += 1
  }
}

function onStepDragEnd() {
  dragFromIndex.value = -1
  dragStepId.value = null
}

async function clearAllSteps() {
  try {
    await ElMessageBox.confirm('确定清空全部步骤？此操作不可撤销（可用版本回滚恢复已保存内容）。', '清空步骤', { type: 'warning' })
  } catch {
    return
  }
  steps.value = []
  selectedStepIds.value = new Set()
  cancelEdit()
  ElMessage.success('已清空步骤')
}

async function openVersionHistory() {
  if (!taskId) {
    ElMessage.warning('请先保存用例后再查看版本')
    return
  }
  showVersionDialog.value = true
  versionLoading.value = true
  try {
    const res = await caseApi.versions(taskId)
    versionList.value = res.data || []
  } catch {
    versionList.value = []
    ElMessage.error('加载版本失败')
  } finally {
    versionLoading.value = false
  }
}

function formatVersionTime(t) {
  return formatTime(t)
}

async function rollbackVersion(ver) {
  try {
    await ElMessageBox.confirm(`确定回滚到 v${ver.version_num}？当前未保存修改将丢失。`, '版本回滚', { type: 'warning' })
  } catch {
    return
  }
  try {
    await caseApi.rollback(taskId, ver.id)
    ElMessage.success(`已回滚至 v${ver.version_num}`)
    showVersionDialog.value = false
    await loadTask()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '回滚失败')
  }
}

async function searchCases(keyword) {
  caseSearchLoading.value = true
  try {
    const res = await caseApi.list({ page: 1, page_size: 30, keyword: keyword || '' })
    caseOptions.value = res.data?.list || res.data?.items || res.data || []
  } catch {
    caseOptions.value = []
  } finally {
    caseSearchLoading.value = false
  }
}

function onInvokeCaseChange(id) {
  const c = caseOptions.value.find(x => x.id === id)
  newStep.case_name = c?.name || ''
}

function confirmInvokeCase() {
  const c = caseOptions.value.find(x => x.id === invokeCaseId.value)
  if (!c) {
    ElMessage.warning('请选择用例')
    return
  }
  if (String(c.id) === String(taskId)) {
    ElMessage.warning('不能调用当前用例自身')
    return
  }
  quickAddStep('invoke_case', { case_id: c.id, case_name: c.name })
  showInvokeCase.value = false
  invokeCaseId.value = null
}

function confirmImportCommon() {
  if (!importCommonNames.value.length) {
    ElMessage.warning('请选择公共步骤')
    return
  }
  for (const name of importCommonNames.value) {
    quickAddStep('invoke_common', { common_step: name })
  }
  importCommonNames.value = []
  showImportCommon.value = false
}

function goCreateCommonStep() {
  const returnTo = route.fullPath
  router.push({ path: '/common-steps/new', query: { returnTo } })
}

function consumeInvokeCommonQuery() {
  const name = route.query.invokeCommon
  if (!name || typeof name !== 'string') return
  quickAddStep('invoke_common', { common_step: name })
  ElMessage.success(`已插入公共步骤「${name}」`)
  const q = { ...route.query }
  delete q.invokeCommon
  router.replace({ path: route.path, query: q })
}

function focusAddPanel(type = 'wait') {
  resetNewStep(type)
  const catalogId = resolveCatalogId({ type })
  if (catalogId) selectedCatalogId.value = catalogId
  rightTab.value = 'add'
  nextTick(() => {
    document.querySelector('.workspace-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

async function goQuickRecord() {
  const lastId = localStorage.getItem(LAST_RECORD_DEVICE_KEY)
  if (lastId) {
    router.push(`/devices/${lastId}/screen?auto_record=1`)
    return
  }
  try {
    const res = await deviceApi.list({ page: 1, page_size: 100 })
    const online = (res.data?.list || []).filter(d => ['online', 'busy'].includes(d.status))
    if (online.length === 1) {
      localStorage.setItem(LAST_RECORD_DEVICE_KEY, String(online[0].id))
      router.push(`/devices/${online[0].id}/screen?auto_record=1`)
      return
    }
    if (!online.length) {
      ElMessage.warning('暂无在线设备，请先在设备管理连接设备')
      router.push('/devices')
      return
    }
    ElMessage.info('请在设备管理中选择设备后开始录制')
    router.push('/devices')
  } catch {
    ElMessage.warning('获取设备列表失败')
    router.push('/devices')
  }
}

function goDevicePool() {
  router.push('/devices')
}

function goElementPicker() {
  const q = {}
  if (meta.app_package) q.package = meta.app_package
  router.push({ path: '/element-picker', query: q })
}

const PICK_TO_CASE_KEY = 'atp_pick_to_case'

function consumePickFromElementPicker() {
  if (String(route.query.from_pick || '') !== '1') return
  let raw = ''
  try {
    raw = sessionStorage.getItem(PICK_TO_CASE_KEY) || ''
    sessionStorage.removeItem(PICK_TO_CASE_KEY)
  } catch {
    return
  }
  if (!raw) return
  let data
  try {
    data = JSON.parse(raw)
  } catch {
    return
  }
  const name = data.display_name || data.element_name || '拾取控件'
  const catalogId = 'ctrl.android.click'
  selectedCatalogId.value = catalogId
  Object.assign(newStep, newStepDefaults(), {
    type: 'click',
    element_name: name,
    display_name: name,
    locator_type: data.locator_type || '',
    locator_value: data.locator_value || '',
    catalog_id: catalogId,
    x: Number(data.x) || 0,
    y: Number(data.y) || 0,
    remark: data.device_label ? `来自控件拾取 · ${data.device_label}` : '来自控件拾取'
  })
  if (data.locator_type === 'ocr' || data.suggested_step_type === 'tap_ocr') {
    newStep.locator_type = data.locator_type || 'ocr'
    newStep.type = 'tap_ocr'
  }
  if (!meta.name?.trim()) {
    meta.name = `拾取用例_${name}`.slice(0, 60)
  }
  rightTab.value = 'add'
  const step = buildStepFromForm()
  if (step) {
    steps.value.push(step)
    ElMessage.success('已带入拾取控件定位，并生成点击步骤')
  } else {
    ElMessage.success('已带入拾取控件定位信息，请确认后添加步骤')
  }
  nextTick(() => {
    document.querySelector('.workspace-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function onDebugCommand(cmd) {
  debugRun(cmd)
}

function onSaveCommand(cmd) {
  if (cmd === 'template') saveAsTemplate()
  else saveTask(cmd)
}

function buildVisualJson() {
  const payload = {
    version: 1,
    human_delay: meta.human_delay,
    on_fail: meta.on_fail || 'fail',
    screenshot_policy: meta.screenshot_policy || 'on_fail',
    steps: steps.value.map(({ id, ...rest }) => rest)
  }
  if (meta.wait_template) payload.wait_template = meta.wait_template
  return JSON.stringify(payload)
}

function buildCasePayload() {
  const payload = {
    name: meta.name.trim(),
    platform: meta.platform,
    script_type: 'visual',
    steps_content: buildVisualJson(),
    app_package: (meta.app_package || '').trim(),
    module_name: meta.module_name || null,
    priority: meta.priority,
    preconditions: meta.preconditions || '',
    expected_result: meta.expected_result || '',
    tags: meta.tags || '',
    dataset_id: meta.dataset_id || null,
    timeout_seconds: meta.timeout_seconds,
    enable_recording: meta.enable_recording,
    case_status: meta.case_status
  }
  const fid = meta.folder_id ?? folderId
  if (fid) payload.folder_id = fid
  else payload.folder_id = null
  return payload
}

function validateCaseRequired({ forActive = false } = {}) {
  const missing = []
  if (!meta.name?.trim()) missing.push('用例名称')
  if (forActive && !steps.value.length) missing.push('至少一个测试步骤')
  if (missing.length) {
    ElMessage.warning(`请完善必填项：${missing.join('、')}`)
    return false
  }
  return true
}

function takeEditorSnapshot() {
  return JSON.stringify({ meta: { ...meta }, steps: steps.value })
}

function markDirty() {
  dirty = takeEditorSnapshot() !== lastSavedSnapshot
}

async function autoSaveDraft() {
  if (!isAssetMode || saving.value || debugging.value) return
  if (!meta.name?.trim()) return
  markDirty()
  if (!dirty) return
  try {
    const prevStatus = meta.case_status
    // 自动保存只落数据，不改变用户选择的正式/草稿状态语义；新建时按草稿创建
    const payload = buildCasePayload()
    if (!taskId) payload.case_status = 'draft'
    if (taskId) {
      await caseApi.update(taskId, payload)
    } else {
      const res = await caseApi.create(payload)
      await router.replace(`/cases/editor/${res.data.id}?asset=1`)
    }
    meta.case_status = prevStatus
    lastSavedSnapshot = takeEditorSnapshot()
    dirty = false
    autoSaveHint.value = `草稿已自动保存 ${new Date().toLocaleTimeString()}`
  } catch (e) {
    autoSaveHint.value = '自动保存失败，请手动保存'
  }
}

function startAutoSave() {
  stopAutoSave()
  autoSaveTimer = setInterval(() => { autoSaveDraft() }, 30000)
}

function stopAutoSave() {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
}

function buildPayload() {
  return {
    name: meta.name,
    platform: meta.platform,
    script_type: 'visual',
    script_content: buildVisualJson(),
    app_package: meta.app_package,
    timeout_seconds: meta.timeout_seconds,
    enable_recording: meta.enable_recording,
    parallel_count: 1,
    use_account_pool: meta.use_account_pool
  }
}

async function debugRun(mode = 'full') {
  if (!validateCaseRequired({ forActive: true })) return
  debugging.value = true
  try {
    let caseId = taskId
    if (isAssetMode) {
      if (caseId) {
        await caseApi.update(caseId, buildCasePayload())
      } else {
        const res = await caseApi.create(buildCasePayload())
        caseId = res.data.id
        router.replace(`/cases/editor/${caseId}?asset=1`)
      }
      lastSavedSnapshot = takeEditorSnapshot()
      dirty = false
      const runRes = await caseApi.run(caseId)
      ElMessage.success(`调试任务 #${runRes.data.id} 已提交`)
      const query = { taskId: runRes.data.id }
      if (mode === 'step') query.mode = 'step'
      router.push({ path: `/cases/${caseId}/debug`, query })
    } else {
      ElMessage.info('请先保存为用例资产后再调试执行')
    }
  } finally {
    debugging.value = false
  }
}

async function saveTask(mode = 'keep') {
  if (mode === 'draft') meta.case_status = 'draft'
  if (mode === 'active') meta.case_status = 'active'
  if (!validateCaseRequired({ forActive: mode === 'active' || meta.case_status === 'active' })) return
  saving.value = true
  try {
    if (isAssetMode) {
      if (taskId) {
        await caseApi.update(taskId, buildCasePayload())
        ElMessage.success(mode === 'active' ? '已提交生效' : mode === 'draft' ? '草稿已保存' : '用例已更新')
      } else {
        const res = await caseApi.create(buildCasePayload())
        ElMessage.success(mode === 'active' ? '已创建并提交生效' : '用例已创建')
        router.replace(`/cases/editor/${res.data.id}?asset=1`)
      }
      lastSavedSnapshot = takeEditorSnapshot()
      dirty = false
      autoSaveHint.value = `已保存 ${new Date().toLocaleTimeString()}`
    } else if (taskId) {
      await taskApi.update(taskId, buildPayload())
      ElMessage.success('任务已更新')
    } else {
      const res = await taskApi.create(buildPayload())
      ElMessage.success('任务已创建')
      router.replace(`/cases/editor/${res.data.id}`)
    }
  } finally {
    saving.value = false
  }
}

async function saveAsTemplate() {
  if (!meta.name?.trim()) {
    ElMessage.warning('请先填写用例名称')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入模板名称', '另存为模板', {
    inputValue: `${meta.name}_模板`,
    confirmButtonText: '保存模板',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (!value) return
  saving.value = true
  try {
    const payload = {
      ...buildCasePayload(),
      name: value.trim(),
      case_status: 'draft',
      folder_id: meta.folder_id || null
    }
    await caseApi.create(payload)
    ElMessage.success('模板已保存，可在用例列表中继续编辑')
  } finally {
    saving.value = false
  }
}

async function previewScript() {
  const res = await taskApi.previewVisual(buildVisualJson())
  previewCode.value = res.data.script
  showPreview.value = true
}

async function goAuthorWorkspace() {
  // 同屏编写需要用例资产；未保存时先落草稿再跳转
  if (!isAssetMode) {
    ElMessage.info('请先保存为用例资产后再使用同屏编写')
    return
  }
  let id = taskId
  if (!id) {
    if (!meta.name?.trim()) {
      ElMessage.warning('请先填写用例名称')
      return
    }
    saving.value = true
    try {
      meta.case_status = meta.case_status || 'draft'
      const res = await caseApi.create(buildCasePayload())
      id = res.data.id
      await router.replace(`/cases/editor/${id}?asset=1`)
    } catch (e) {
      ElMessage.error(e?.message || '创建用例失败')
      return
    } finally {
      saving.value = false
    }
  } else if (steps.value.length) {
    // 带上当前未保存的编辑内容
    try {
      await caseApi.update(id, buildCasePayload())
    } catch {
      /* 仍允许进入，以服务端最新为准 */
    }
  }
  router.push({
    path: `/cases/editor/${id}/author`,
    query: {}
  })
}

async function loadTask() {
  if (!taskId) {
    lastSavedSnapshot = takeEditorSnapshot()
    return
  }
  if (isAssetMode) {
    const res = await caseApi.get(taskId)
    const c = res.data
    meta.name = c.name
    meta.platform = c.platform
    meta.app_package = c.app_package || ''
    meta.module_name = c.module_name || ''
    meta.priority = normalizePriorityByte(c.priority)
    meta.preconditions = c.preconditions || ''
    meta.expected_result = c.expected_result || ''
    meta.tags = c.tags || ''
    meta.dataset_id = c.dataset_id || null
    meta.timeout_seconds = c.timeout_seconds
    meta.enable_recording = c.enable_recording
    meta.case_status = c.case_status || 'draft'
    meta.folder_id = c.folder_id || null
    try {
      const parsed = JSON.parse(c.steps_content)
      meta.human_delay = parsed.human_delay || false
      meta.wait_template = parsed.wait_template || 'standard'
      meta.on_fail = parsed.on_fail || 'fail'
      meta.screenshot_policy = parsed.screenshot_policy || 'on_fail'
      steps.value = (parsed.steps || []).map(s => ({
        id: stepSeq++,
        enabled: s.enabled !== false,
        disable_reason: s.disable_reason || '',
        ...s,
        on_fail: normalizeOnFail(s.on_fail)
      }))
    } catch { steps.value = [] }
    lastSavedSnapshot = takeEditorSnapshot()
    dirty = false
    return
  }
  const res = await taskApi.get(taskId)
  const task = res.data
  meta.name = task.name
  meta.platform = task.platform
  meta.app_package = task.app_package || ''
  meta.timeout_seconds = task.timeout_seconds
  meta.enable_recording = task.enable_recording
  meta.use_account_pool = task.use_account_pool || false
  try {
    const parsed = JSON.parse(task.script_content)
    meta.human_delay = parsed.human_delay || false
    meta.wait_template = parsed.wait_template || 'standard'
    meta.on_fail = parsed.on_fail || 'fail'
    meta.screenshot_policy = parsed.screenshot_policy || 'on_fail'
    steps.value = (parsed.steps || []).map(s => ({
      id: stepSeq++,
      enabled: s.enabled !== false,
      ...s,
      on_fail: normalizeOnFail(s.on_fail)
    }))
  } catch {
    steps.value = []
  }
  lastSavedSnapshot = takeEditorSnapshot()
}

function normalizePriorityByte(p) {
  const v = String(p ?? '1').toLowerCase()
  if (['0', 'p0', 'critical'].includes(v)) return 0
  if (['1', 'p1', 'high', '高'].includes(v)) return 1
  if (['2', 'p2', 'medium', 'mid', '中'].includes(v)) return 2
  if (['3', 'p3', 'low', '低'].includes(v)) return 3
  const n = Number(p)
  return Number.isFinite(n) ? Math.min(3, Math.max(0, n)) : 1
}

onMounted(async () => {
  loadFolders()
  try { commonSteps.value = (await commonStepApi.list()).data } catch { /* ignore */ }
  try { dataFactoryTemplates.value = (await dataFactoryApi.listTemplates()).data } catch { /* ignore */ }
  try {
    const res = await datasetApi.list()
    datasets.value = res.data?.list || res.data || []
  } catch { datasets.value = [] }
  await loadTask()
  consumePickFromElementPicker()
  consumeInvokeCommonQuery()
  if (isAssetMode) {
    startAutoSave()
    watch([meta, steps], () => { markDirty() }, { deep: true })
  }
})

onBeforeUnmount(() => {
  stopAutoSave()
})
</script>

<script>
/** 字段悬浮释义：用自定义指令避免每个 label 包一层 Tooltip 组件 */
export default {
  directives: {
    tooltip: {
      mounted(el, binding) {
        el.setAttribute('title', binding.value || '')
        el.style.cursor = 'help'
        el.style.borderBottom = '1px dashed rgba(100,116,139,0.45)'
      },
      updated(el, binding) {
        el.setAttribute('title', binding.value || '')
      }
    }
  }
}
</script>

<style scoped>
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  align-items: center;
}
.btn-secondary {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #e2e8f0;
  --el-button-hover-border-color: #cbd5e1;
}

.main-row {
  margin-bottom: 16px;
  align-items: stretch;
}
.main-row > .el-col {
  display: flex;
  flex-direction: column;
}
.meta-card,
.steps-card,
.workspace-card {
  flex: 1;
  width: 100%;
  display: flex !important;
  flex-direction: column;
  height: 100%;
  margin-bottom: 0 !important;
}
.meta-card :deep(.el-card__header),
.steps-card :deep(.el-card__header),
.workspace-card :deep(.el-card__header) {
  flex-shrink: 0;
}
.meta-card :deep(.el-card__body),
.steps-card :deep(.el-card__body),
.workspace-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.steps-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.steps-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.steps-count { font-size: 12px; color: var(--atp-text-secondary); }

.steps-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.steps-empty {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px 16px;
}

.form-group { margin-bottom: 8px; }
.form-group--advanced {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--atp-border-light, #eef2f7);
}
.form-group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--atp-text);
  margin-bottom: 12px;
}
.pkg-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.pkg-row .el-input { flex: 1; }
.switch-with-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.field-hint {
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.4;
}

.steps-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}
.steps-header-left { display: flex; flex-direction: column; gap: 2px; }
.steps-title { font-weight: 600; }
.steps-hint { font-size: 12px; color: var(--atp-text-secondary); }
.steps-header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}
.empty-title {
  margin: 0 0 16px;
  font-size: 15px;
  color: var(--atp-text-secondary);
}
.empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 10px;
  margin-bottom: 8px;
  border-radius: 10px;
  border: 1px solid transparent;
  transition: box-shadow 0.15s, transform 0.15s;
}
.step-item:hover { box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06); }
.step-item.is-dragging {
  opacity: 0.55;
}
.step-item.disabled { opacity: 0.55; }
.step-item.selected {
  outline: 1px solid var(--el-color-primary);
}
.drag-handle {
  cursor: grab;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1;
  user-select: none;
  padding: 0 2px;
}
.drag-handle:active { cursor: grabbing; }
.step-item.tone-wait { background: #f8fafc; border-color: #e2e8f0; }
.step-item.tone-click { background: #eff6ff; border-color: #bfdbfe; }
.step-item.tone-assert { background: #ecfdf5; border-color: #a7f3d0; }
.step-item.tone-app { background: #fff7ed; border-color: #fed7aa; }
.step-item.tone-default { background: #f8fafc; border-color: #e2e8f0; }

.comment-list {
  max-height: 320px;
  overflow-y: auto;
}
.comment-empty {
  color: #94a3b8;
  font-size: 13px;
  padding: 16px 0;
  text-align: center;
}
.comment-item {
  padding: 10px 0;
  border-bottom: 1px solid #eef2f7;
}
.comment-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
  font-size: 12px;
}
.comment-author { font-weight: 600; color: #334155; }
.comment-time { color: #94a3b8; flex: 1; }
.comment-body {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
  white-space: pre-wrap;
}

.step-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--atp-primary), var(--atp-brand-400, #38bdf8));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.step-body {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}
.step-desc {
  color: var(--atp-text-secondary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-locator {
  color: var(--el-color-info);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-remark {
  color: #64748b;
  font-size: 12px;
  font-style: italic;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.autosave-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}
.field-warn {
  margin-top: 4px;
  font-size: 12px;
  color: #ea580c;
  line-height: 1.4;
}

.workspace-card {
  flex: 1;
  width: 100%;
  display: flex !important;
  flex-direction: column;
  height: 100%;
  margin-bottom: 0 !important;
}
.workspace-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.right-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.right-tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
  margin-bottom: 12px;
}
.right-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.right-tabs :deep(.el-tab-pane) {
  height: 100%;
}
.vce-fab-record {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 1200;
  border: none;
  border-radius: 999px;
  padding: 12px 18px;
  background: linear-gradient(135deg, var(--atp-primary, #2563eb), #0ea5e9);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.35);
  cursor: pointer;
}
.vce-fab-record:hover {
  filter: brightness(1.05);
}

.pool-picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.pool-pick-count {
  font-size: 13px;
  color: var(--atp-text-secondary, #64748b);
}
.step-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex-shrink: 0;
}
.btn-copy {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}

.add-panel { margin-top: 4px; }
.add-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.quick-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--atp-border-light, #e2e8f0);
}
.quick-group {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
}
.quick-label {
  flex-shrink: 0;
  width: 88px;
  font-size: 12px;
  font-weight: 600;
  color: var(--atp-text-secondary);
  line-height: 28px;
}
.quick-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
}
.add-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}
.add-tip {
  font-size: 12px;
  color: #94a3b8;
}
.form-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.script-code-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 992px) {
  .steps-header-actions { justify-content: flex-start; }
  .quick-label { width: 100%; }
}
</style>
