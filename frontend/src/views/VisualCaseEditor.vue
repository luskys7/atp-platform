<template>
  <div class="page-container vce-page">
    <header class="vce-topbar">
      <div class="vce-topbar-left">
        <h2 class="vce-title">{{ taskId ? '编辑测试用例' : '新增测试用例' }}</h2>
        <nav class="flow-progress" aria-label="编写流程">
          <button
            v-for="(stage, idx) in FLOW_STAGES"
            :key="stage.key"
            type="button"
            class="flow-step"
            :class="{
              active: flowStage === stage.key,
              done: FLOW_STAGE_ORDER.indexOf(flowStage) > idx,
              current: flowStage === stage.key
            }"
            @click="goToFlowStage(stage.key)"
          >
            <span class="flow-dot">{{ idx + 1 }}</span>
            <span class="flow-label">{{ stage.label }}</span>
          </button>
        </nav>
      </div>
      <div class="vce-topbar-actions">
        <el-dropdown split-button type="primary" class="btn-save" :disabled="saving" @click="saveTask('keep')" @command="onSaveCommand">
          {{ saving ? '保存中…' : '保存' }}
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="draft">保存草稿</el-dropdown-item>
              <el-dropdown-item command="active">提交生效</el-dropdown-item>
              <el-dropdown-item command="template">另存为模板</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-dropdown split-button type="warning" class="btn-debug" :disabled="debugging" @click="debugRun('full')" @command="onDebugCommand">
          {{ debugging ? '调试中…' : '调试执行' }}
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="full">完整调试</el-dropdown-item>
              <el-dropdown-item command="step">单步调试</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button type="primary" plain class="btn-preview" @click="previewScript">预览脚本</el-button>

        <el-dropdown trigger="click" @command="onMoreCommand">
          <el-button class="btn-more">更多 <span class="more-caret">▾</span></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="author">同屏编写</el-dropdown-item>
              <el-dropdown-item command="versions" :disabled="!taskId">版本历史</el-dropdown-item>
              <el-dropdown-item command="comments" :disabled="!taskId">协作批注</el-dropdown-item>
              <el-dropdown-item divided command="back">返回</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <el-row :gutter="16" class="main-row">
      <el-col :xs="24" :lg="6">
        <AppCard :hover="false" class="meta-card">
          <el-form :model="meta" label-position="top" class="meta-form" @submit.prevent>
            <section ref="basicInfoRef" class="meta-section">
              <div class="meta-section-title">基础信息 <span class="req-hint">必填标红</span></div>

              <el-form-item required class="is-required-strong">
                <template #label>
                  <span class="req-label">用例名称</span>
                </template>
                <el-input
                  v-model="meta.name"
                  placeholder="请填写用例名称"
                  maxlength="80"
                  show-word-limit
                />
              </el-form-item>

              <el-form-item label="所属目录">
                <div class="folder-row">
                  <el-select v-model="meta.folder_id" clearable placeholder="根目录" class="folder-select">
                    <el-option label="根目录" :value="null" />
                    <el-option v-for="f in flatFolders" :key="f.id" :label="f.label" :value="f.id" />
                  </el-select>
                  <el-button size="default" @click="openFolderDialog">新建</el-button>
                </div>
              </el-form-item>

              <el-form-item label="业务模块">
                <el-select v-model="meta.module_name" clearable filterable allow-create placeholder="选择或输入模块" style="width:100%">
                  <el-option v-for="m in MODULE_OPTIONS" :key="m" :label="m" :value="m" />
                </el-select>
              </el-form-item>

              <el-form-item label="优先级">
                <el-radio-group v-model="meta.priority" class="priority-group">
                  <el-radio-button :value="0">P0</el-radio-button>
                  <el-radio-button :value="1">P1</el-radio-button>
                  <el-radio-button :value="2">P2</el-radio-button>
                  <el-radio-button :value="3">P3</el-radio-button>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="前置条件">
                <el-input
                  v-model="meta.preconditions"
                  type="textarea"
                  :rows="1"
                  class="compact-textarea"
                  placeholder="例：已登录、设备在线"
                />
              </el-form-item>

              <el-form-item label="预期结果">
                <el-input
                  v-model="meta.expected_result"
                  type="textarea"
                  :rows="1"
                  class="compact-textarea"
                  placeholder="例：进入首页并展示昵称"
                />
              </el-form-item>
            </section>

            <div v-if="autoSaveHint" class="autosave-hint">{{ autoSaveHint }}</div>
          </el-form>
        </AppCard>
      </el-col>

      <el-col :xs="24" :lg="18">
        <AppCard :hover="false" class="workspace-card">
          <el-tabs v-model="rightTab" class="right-tabs">
            <el-tab-pane name="list" label="测试步骤列表">
              <div ref="stepsAreaRef" class="steps-body">
                <div class="steps-toolbar-panel">
                  <div class="toolbar-row toolbar-row--search">
                    <el-input
                      v-model="stepKeyword"
                      clearable
                      prefix-icon="Search"
                      placeholder="搜索步骤类型 / 摘要 / 控件"
                      class="step-search"
                    />
                    <span class="steps-count">共 {{ topLevelStepItems.length }} / {{ topLevelStepCount }} 步</span>
                    <span class="steps-drag-hint">可拖拽排序</span>
                  </div>

                  <div class="toolbar-row toolbar-groups">
                    <div class="tb-group">
                      <span class="tb-label">顺序</span>
                      <el-button-group>
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
                      </el-button-group>
                    </div>

                    <div class="tb-group">
                      <span class="tb-label">块结构</span>
                      <el-button-group>
                        <el-button size="small" :disabled="!hasBlockSteps" @click="expandAllBlocks">全部展开</el-button>
                        <el-button size="small" :disabled="!hasBlockSteps" @click="collapseAllBlocks">全部收起</el-button>
                      </el-button-group>
                    </div>

                    <div class="tb-group">
                      <span class="tb-label">批量</span>
                      <el-button-group>
                        <el-button size="small" :disabled="!hasStepSelection" @click="batchSetStepsEnabled(true)">启用</el-button>
                        <el-button size="small" :disabled="!hasStepSelection" @click="batchSetStepsEnabled(false)">禁用</el-button>
                        <el-button size="small" :disabled="!hasStepSelection" @click="batchCopySteps">复制</el-button>
                        <el-button size="small" type="danger" plain :disabled="!hasStepSelection" @click="batchDeleteSteps">删除</el-button>
                      </el-button-group>
                      <el-button size="small" @click="openFindReplace">查找替换</el-button>
                      <el-button size="small" type="danger" plain :disabled="!steps.length" @click="clearAllSteps">清空</el-button>
                      <el-button size="small" :disabled="!hasStepSelection" @click="saveSelectionAsCommon">另存公共</el-button>
                    </div>

                    <div class="tb-group">
                      <span class="tb-label">公共</span>
                      <el-button size="small" type="primary" plain @click="showImportCommon = true">导入公共步骤</el-button>
                      <el-button size="small" @click="goCreateCommonStep">新建公共步骤</el-button>
                    </div>

                    <div class="tb-group">
                      <span class="tb-label">逻辑</span>
                      <el-button size="small" @click="showInvokeCase = true">调用用例</el-button>
                      <el-button size="small" @click="addBranchStep">if判断</el-button>
                      <el-button size="small" @click="addElseIfStep">else if</el-button>
                      <el-button size="small" @click="addElseStep">else</el-button>
                      <el-button size="small" @click="addLoopStep">while循环</el-button>
                    </div>
                  </div>
                </div>

                <div class="steps-scroll">
                  <div v-if="!steps.length" class="steps-empty">
                    <p class="empty-title">暂无测试步骤</p>
                    <p class="empty-sub">选择一种方式开始编排，推荐可视化新增</p>
                    <div class="empty-entries">
                      <button type="button" class="empty-entry empty-entry--primary" @click="focusAddPanel('click')">
                        <span class="entry-badge">① 主推</span>
                        <strong>可视化新增步骤</strong>
                        <span>投屏拾取控件，拖拽编排点击 / 输入 / 滑动等基础动作</span>
                      </button>
                      <button type="button" class="empty-entry" @click="showImportCommon = true">
                        <span class="entry-badge">②</span>
                        <strong>公共步骤库导入</strong>
                        <span>复用登录、初始化等全局 / 项目公共流程</span>
                      </button>
                      <button type="button" class="empty-entry" @click="openPoolFromEmpty">
                        <span class="entry-badge">③</span>
                        <strong>元素库选取搭建</strong>
                        <span>从控件库选取预设元素，快速组装步骤</span>
                      </button>
                    </div>
                    <p class="empty-advanced">进阶：顶部「更多 → 同屏编写」可手写脚本</p>
                  </div>

                  <div v-else class="steps-list">
                    <template v-for="item in topLevelStepItems" :key="item.step.id ?? item.index">
                      <!-- 公共步骤 -->
                      <div v-if="item.step.type === 'invoke_common'" class="step-block">
                        <div
                          class="step-meta-row"
                          @click.stop
                          @mousedown="onRemarkMouseDown"
                          @dragstart.stop.prevent
                        >
                          <span class="step-index-label">步骤{{ item.displayNo }}：</span>
                          <el-input
                            v-model="item.step.remark"
                            class="step-remark-input"
                            size="small"
                            maxlength="200"
                            placeholder="添加描述"
                          />
                        </div>
                        <div
                          class="step-item invoke-common-card"
                          :class="{
                            disabled: item.step.enabled === false,
                            selected: selectedStepIds.has(item.step.id),
                            'is-dragging': isStepDraggingIndex(item.index)
                          }"
                          draggable="true"
                          @dragstart="onStepDragStart($event, item.index)"
                          @dragover.prevent="onStepDragOver($event, item.index)"
                          @drop.prevent="onStepDrop($event, item.index)"
                          @dragend="onStepDragEnd"
                        >
                          <div class="icc-top">
                            <span v-if="item.step.enabled === false" class="icc-disabled-hint">{{ item.step.disable_reason || '已禁用' }}</span>
                            <div class="icc-top-actions">
                              <el-switch v-model="item.step.enabled" size="small" @click.stop />
                              <el-button size="small" link type="primary" @click="editStep(item.index)">编辑</el-button>
                              <el-button size="small" link @click="beginInsertAt(item.index, 'after')">此后插入</el-button>
                              <el-button size="small" link class="btn-copy" @click="copyStep(item.index)">复制</el-button>
                              <el-button size="small" link :disabled="item.index === 0" @click="moveStep(item.index, -1)">上移</el-button>
                              <el-button size="small" link :disabled="item.index === steps.length - 1" @click="moveStep(item.index, 1)">下移</el-button>
                              <el-button size="small" link type="danger" @click="removeStep(item.index)">删除</el-button>
                            </div>
                          </div>
                          <div class="icc-main">
                            <span class="common-step-use-tag">使用公共步骤</span>
                            <span class="common-step-name-box" :title="stepSummary(item.step)">{{ stepSummary(item.step) }}</span>
                            <el-button class="icc-view-btn" size="small" @click="viewCommonStep(item.step)">查看步骤</el-button>
                          </div>
                        </div>
                      </div>

                      <!-- 判断 / 循环 -->
                      <div v-else-if="isFlowHeaderStep(item.step)" class="step-block">
                        <div
                          class="step-meta-row"
                          @click.stop
                          @mousedown="onRemarkMouseDown"
                          @dragstart.stop.prevent
                        >
                          <span class="step-index-label">步骤{{ item.displayNo }}：</span>
                          <el-input
                            v-model="item.step.remark"
                            class="step-remark-input"
                            size="small"
                            maxlength="200"
                            placeholder="添加描述"
                          />
                        </div>
                        <div
                          class="step-item judge-list-card"
                          :class="{
                            disabled: item.step.enabled === false,
                            selected: selectedStepIds.has(item.step.id),
                            'is-dragging': isStepDraggingIndex(item.index),
                            'is-collapsed': collapsedBlockIds.has(item.collapseKey)
                          }"
                          @dragover.prevent="onStepDragOver($event, item.index)"
                          @drop.prevent="onStepDrop($event, item.index)"
                        >
                        <div
                          v-if="item.step.type === 'loop'"
                          class="jlc-loop-bar"
                          @click="toggleBlockCollapse(item.collapseKey)"
                        >
                          <span class="jlc-loop-tag">循环结构</span>
                          <span class="jlc-loop-hint">点击展开/收起循环体</span>
                          <span class="jlc-chevron" :class="{ open: !collapsedBlockIds.has(item.collapseKey) }">▾</span>
                        </div>
                        <div class="jlc-head" @click="toggleBlockCollapse(item.collapseKey)">
                          <span
                            class="drag-handle jlc-drag"
                            title="拖拽排序"
                            draggable="true"
                            @click.stop
                            @dragstart.stop="onStepDragStart($event, item.index)"
                            @dragend="onStepDragEnd"
                          >⋮⋮</span>
                          <el-checkbox
                            class="jlc-check"
                            :model-value="selectedStepIds.has(item.step.id)"
                            @click.stop
                            @change="(v) => toggleStepSelect(item.step.id, v)"
                          />
                          <span class="jlc-if-icon">{{ timelineBlockTag(item.step) }}</span>
                          <div class="jlc-mid">
                            <span class="jlc-action-pill">{{ timelineJudgeActionText(item.step) }}</span>
                            <span class="jlc-assert-label">断言：</span>
                            <span
                              v-if="judgeNeedsElement(item.step)"
                              class="jlc-pill"
                              :class="{ 'jlc-pill-warn': !judgeDisplayElement(item) }"
                              :title="judgeDisplayElement(item) || '请编辑 if 步骤并选择要判断的控件'"
                            >{{ judgeDisplayElement(item) || '未指定控件' }}</span>
                            <span v-if="judgeDisplayAssertText(item)" class="jlc-assert-state">{{ judgeDisplayAssertText(item) }}</span>
                            <span class="jlc-status">无异常</span>
                            <el-tag
                              v-if="collapsedBlockIds.has(item.collapseKey) && item.collapsedCount > 0"
                              size="small"
                              type="info"
                              effect="plain"
                            >已收起 {{ item.collapsedCount }} 步</el-tag>
                          </div>
                          <el-switch v-model="item.step.enabled" size="small" @click.stop />
                          <span class="jlc-chevron" :class="{ open: !collapsedBlockIds.has(item.collapseKey) }">▾</span>
                        </div>
                        <div
                          v-show="!collapsedBlockIds.has(item.collapseKey)"
                          class="jlc-body"
                          @click.stop
                        >
                          <div
                            v-for="(child, cIdx) in judgeBodyChildren(item)"
                            :key="child.step.id ?? `c-${child.index}`"
                            class="jlc-child"
                          >
                            <div class="jlc-child-rail">
                              <span
                                class="jlc-child-dot"
                                :class="{ 'dot-block': isFlowHeaderStep(child.step) }"
                              />
                            </div>
                            <div class="jlc-child-content">
                              <!-- 嵌套判断 -->
                              <div
                                v-if="isFlowHeaderStep(child.step)"
                                class="judge-list-card jlc-nested-judge"
                                :class="{ 'is-collapsed': collapsedBlockIds.has(child.collapseKey) }"
                              >
                                <div class="jlc-head" @click="toggleBlockCollapse(child.collapseKey)">
                                  <span class="jlc-if-icon">{{ timelineBlockTag(child.step) }}</span>
                                  <div class="jlc-mid">
                                    <span class="jlc-action-pill">{{ timelineJudgeActionText(child.step) }}</span>
                                    <span class="jlc-assert-label">断言：</span>
                                    <span
                                      v-if="judgeNeedsElement(child.step)"
                                      class="jlc-pill"
                                      :class="{ 'jlc-pill-warn': !judgeDisplayElement(child) }"
                                    >{{ judgeDisplayElement(child) || '未指定控件' }}</span>
                                    <span v-if="judgeDisplayAssertText(child)" class="jlc-assert-state">{{ judgeDisplayAssertText(child) }}</span>
                                    <span class="jlc-status">无异常</span>
                                  </div>
                                  <el-switch v-model="child.step.enabled" size="small" @click.stop />
                                  <span class="jlc-chevron" :class="{ open: !collapsedBlockIds.has(child.collapseKey) }">▾</span>
                                </div>
                                <div v-show="!collapsedBlockIds.has(child.collapseKey)" class="jlc-body">
                                  <div
                                    v-for="(g, gIdx) in judgeBodyChildren(child)"
                                    :key="g.step.id ?? `g-${g.index}`"
                                    class="jlc-child"
                                  >
                                    <div class="jlc-child-rail">
                                      <span class="jlc-child-dot" />
                                    </div>
                                    <div class="jlc-child-content">
                                      <div class="jlc-child-step">
                                        <div class="jlc-child-label">步骤{{ gIdx + 1 }}:</div>
                                        <div class="jlc-child-main">
                                          <template v-for="(p, pIdx) in timelineChildParts(g.step)" :key="`${g.index}-${pIdx}`">
                                            <span v-if="p.kind === 'action'" class="jlc-action-pill">{{ p.text }}</span>
                                            <span v-else-if="p.kind === 'param'" class="jlc-pill" :title="p.text">{{ p.text }}</span>
                                            <span v-else class="jlc-child-text">{{ p.text }}</span>
                                          </template>
                                          <el-button
                                            v-if="g.step.type === 'invoke_common'"
                                            size="small"
                                            plain
                                            @click="viewCommonStep(g.step)"
                                          >查看步骤</el-button>
                                          <el-button size="small" link type="primary" @click="editStep(g.index)">编辑</el-button>
                                          <el-button size="small" link type="danger" @click="removeStep(g.index)">删除</el-button>
                                        </div>
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              </div>
                              <!-- 普通嵌套步骤 -->
                              <div v-else class="jlc-child-step">
                                <div class="jlc-child-label">步骤{{ cIdx + 1 }}:</div>
                                <div class="jlc-child-main">
                                  <template v-for="(p, pIdx) in timelineChildParts(child.step)" :key="`${child.index}-${pIdx}`">
                                    <span v-if="p.kind === 'action'" class="jlc-action-pill">{{ p.text }}</span>
                                    <span v-else-if="p.kind === 'param'" class="jlc-pill" :title="p.text">{{ p.text }}</span>
                                    <span v-else class="jlc-child-text">{{ p.text }}</span>
                                  </template>
                                  <el-button
                                    v-if="child.step.type === 'invoke_common'"
                                    class="icc-view-btn"
                                    size="small"
                                    @click="viewCommonStep(child.step)"
                                  >查看步骤</el-button>
                                  <el-button size="small" link type="primary" @click="editStep(child.index)">编辑</el-button>
                                  <el-button size="small" link type="danger" @click="removeStep(child.index)">删除</el-button>
                                </div>
                              </div>
                            </div>
                          </div>
                          <div v-if="!judgeBodyChildren(item).length" class="jlc-empty">块内暂无步骤，可点「块内插入」添加</div>
                        </div>
                        <div class="jlc-actions" @click.stop>
                          <el-button size="small" link type="primary" @click="editStep(item.index)">编辑</el-button>
                          <el-button size="small" link @click="beginInsertAt(item.index, 'after')">块内插入</el-button>
                          <el-button size="small" link class="btn-copy" @click="copyStep(item.index)">复制</el-button>
                          <el-button size="small" link :disabled="item.index === 0" @click="moveStep(item.index, -1)">上移</el-button>
                          <el-button size="small" link :disabled="item.index === steps.length - 1" @click="moveStep(item.index, 1)">下移</el-button>
                          <el-button size="small" link type="danger" @click="removeStep(item.index)">删除</el-button>
                        </div>
                        </div>
                      </div>

                      <!-- 普通顶层步骤 -->
                      <div v-else class="step-block">
                        <div
                          class="step-meta-row"
                          @click.stop
                          @mousedown="onRemarkMouseDown"
                          @dragstart.stop.prevent
                        >
                          <span class="step-index-label">步骤{{ item.displayNo }}：</span>
                          <el-input
                            v-model="item.step.remark"
                            class="step-remark-input"
                            size="small"
                            maxlength="200"
                            placeholder="添加描述"
                          />
                        </div>
                        <div
                          class="step-item"
                          :class="[
                            stepToneClass(item.step.type),
                            {
                              disabled: item.step.enabled === false,
                              selected: selectedStepIds.has(item.step.id),
                              'is-dragging': isStepDraggingIndex(item.index)
                            }
                          ]"
                          draggable="true"
                          @dragstart="onStepDragStart($event, item.index)"
                          @dragover.prevent="onStepDragOver($event, item.index)"
                          @drop.prevent="onStepDrop($event, item.index)"
                          @dragend="onStepDragEnd"
                        >
                          <span class="drag-handle" title="按住此处或整行拖拽排序">⋮⋮</span>
                          <el-checkbox
                            :model-value="selectedStepIds.has(item.step.id)"
                            @change="(v) => toggleStepSelect(item.step.id, v)"
                          />
                          <div class="step-body">
                            <div class="step-main-row">
                              <el-tag size="small" :type="stepTagType(item.step.type)" effect="plain">
                                {{ stepTypeLabel(item.step) }}
                              </el-tag>
                              <span class="step-desc">{{ stepSummary(item.step) }}</span>
                              <span v-if="stepLocator(item.step)" class="step-locator">{{ stepLocator(item.step) }}</span>
                              <el-tag v-if="item.step.enabled === false" size="small" type="info">
                                {{ item.step.disable_reason || '已禁用' }}
                              </el-tag>
                              <el-switch
                                v-if="item.step.type !== 'end_block'"
                                v-model="item.step.enabled"
                                size="small"
                                class="step-enable-switch"
                              />
                            </div>
                          </div>
                          <div class="step-actions">
                            <el-button v-if="item.step.type !== 'end_block'" size="small" type="primary" plain @click="editStep(item.index)">编辑</el-button>
                            <el-button
                              size="small"
                              plain
                              @click="beginInsertAt(item.index, item.step.type === 'end_block' ? 'before' : 'after')"
                            >{{ item.step.type === 'end_block' ? '块内插入' : '此后插入' }}</el-button>
                            <el-button
                              v-if="item.step.type !== 'end_block' && !ASSERT_TYPES.has(item.step.type)"
                              size="small"
                              type="success"
                              plain
                              @click="addAssertAfter(item.index)"
                            >添加断言</el-button>
                            <el-button size="small" class="btn-copy" @click="copyStep(item.index)">复制</el-button>
                            <el-button size="small" plain :disabled="item.index === 0" @click="moveStep(item.index, -1)">上移</el-button>
                            <el-button size="small" plain :disabled="item.index === steps.length - 1" @click="moveStep(item.index, 1)">下移</el-button>
                            <el-button size="small" type="danger" plain @click="removeStep(item.index)">删除</el-button>
                          </div>
                        </div>
                      </div>
                    </template>
                    <el-empty v-if="!topLevelStepItems.length" description="未找到匹配步骤" :image-size="64" />
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
                :insert-at-index="insertAtIndex"
                :common-steps="commonSteps"
                :locator-hint="locatorHint"
                :added-steps="steps"
                :type-label-fn="stepTypeLabel"
                :summary-fn="stepSummary"
                :tag-type-fn="stepTagType"
                @update:model-value="onNewStepUpdate"
                @update:catalog-id="(id) => (selectedCatalogId = id)"
                @update:expanded-keys="(keys) => (treeExpandedKeys = keys)"
                @field-change="onStepFieldChange"
                @submit="submitStepForm"
                @cancel="cancelEdit"
                @pick="goElementPicker"
                @pool="onOpenPool"
                @create-common="goCreateCommonStep"
                @edit="editStep"
                @remove="removeStep"
              />
            </el-tab-pane>
          </el-tabs>
        </AppCard>
      </el-col>
    </el-row>

    <button class="vce-fab-record" type="button" title="一键录制" @click="goQuickRecord">一键录制</button>

    <el-dialog v-model="showFolderDialog" title="新建目录" width="400px">
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="目录名" required>
          <el-input v-model="folderForm.name" placeholder="请输入目录名" maxlength="40" />
        </el-form-item>
        <el-form-item label="上级目录">
          <el-select v-model="folderForm.parent_id" placeholder="根目录" clearable style="width:100%">
            <el-option label="根目录" :value="null" />
            <el-option v-for="f in flatFolders" :key="f.id" :label="f.label" :value="f.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFolderDialog = false">取消</el-button>
        <el-button type="primary" :loading="folderSaving" @click="saveFolder">创建</el-button>
      </template>
    </el-dialog>

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

    <el-dialog
      v-model="showCommonView"
      :title="commonViewTitle"
      width="760px"
      destroy-on-close
      class="common-view-dialog"
      @closed="commonViewStack = []; commonViewRow = null; commonViewSteps = []"
    >
      <div v-loading="commonViewLoading" class="common-view-body">
        <p v-if="commonViewRow?.description" class="common-view-desc">{{ commonViewRow.description }}</p>
        <el-empty
          v-if="!commonViewLoading && !commonViewTimelineNodes.length"
          description="该公共步骤暂无内部步骤"
          :image-size="72"
        />
        <CommonStepTimeline
          v-else-if="commonViewTimelineNodes.length"
          :nodes="commonViewTimelineNodes"
          :type-label="stepTypeLabel"
          :step-summary="stepSummary"
          :param-text="timelineParamText"
          :assert-text="timelineAssertText"
          :block-tag="timelineBlockTag"
          :judge-action-text="timelineJudgeActionText"
          :element-name="flowElementName"
          :child-parts="timelineChildParts"
          @view-common="viewCommonStep"
          @save-script="saveCommonViewScript"
        />
      </div>
      <template #footer>
        <el-button v-if="commonViewStack.length" @click="backCommonView">返回上级</el-button>
        <el-button @click="closeCommonView">关闭</el-button>
        <el-button
          v-if="commonViewRow?.id"
          type="primary"
          plain
          @click="goEditViewedCommonStep"
        >去编辑公共步骤</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showPoolPicker"
      :title="poolPickerTitle"
      width="720px"
      destroy-on-close
      @closed="onPoolPickerClosed"
    >
      <div class="pool-picker-toolbar">
        <el-select
          v-model="poolPageFilter"
          clearable
          filterable
          placeholder="全部页面"
          style="width: 160px"
        >
          <el-option label="全部页面" value="" />
          <el-option
            v-for="p in poolPageOptions"
            :key="p"
            :label="p"
            :value="p"
          />
        </el-select>
        <el-input v-model="poolKeyword" clearable placeholder="搜索控件名称 / 页面 / 定位" style="flex:1" />
        <el-checkbox v-if="isCoordPoolMode" v-model="poolBoundsOnly">仅坐标定位</el-checkbox>
      </div>
      <el-table
        ref="poolTableRef"
        :data="filteredPoolItems"
        height="360"
        row-key="id"
        highlight-current-row
        :row-class-name="poolRowClassName"
        @row-click="onPoolRowClick"
        @selection-change="onPoolSelectionChange"
      >
        <el-table-column v-if="!isCoordPoolMode" type="selection" width="48" />
        <el-table-column v-else width="48" align="center">
          <template #default="{ row }">
            <span class="pool-radio" :class="{ checked: selectedPoolRow?.id === row.id }" />
          </template>
        </el-table-column>
        <el-table-column prop="element_name" label="控件名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="page_name" label="页面" width="120" show-overflow-tooltip />
        <el-table-column prop="locator_type" label="定位类型" width="100" />
        <el-table-column prop="locator_value" label="定位表达式" min-width="180" show-overflow-tooltip />
      </el-table>
      <p v-if="!filteredPoolItems.length" class="pool-empty">暂无匹配控件，可先在控件库录入坐标/定位条目</p>
      <template #footer>
        <div class="pool-picker-footer">
          <span class="pool-pick-count">
            {{ isCoordPoolMode
              ? (selectedPoolRow ? `已选：${selectedPoolRow.element_name || '未命名'}` : '请点击一行选择')
              : `已选 ${selectedPoolRows.length} 项` }}
          </span>
          <div>
            <el-button v-if="!isCoordPoolMode" plain @click="saveStepToPool" :disabled="!canSaveToPool">另存控件库</el-button>
            <el-button @click="showPoolPicker = false">取消</el-button>
            <el-button
              type="primary"
              :disabled="isCoordPoolMode ? !selectedPoolRow : !selectedPoolRows.length"
              @click="confirmPoolSelection"
            >确认选用</el-button>
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
import CommonStepTimeline from '@/components/case-editor/CommonStepTimeline.vue'
import { getCatalogLeaf, resolveLeafFields, normalizeOnFail } from '@/config/stepCatalog'
import {
  conditionLabel,
  conditionNeedsLocator,
  conditionNeedsExpected,
  conditionNeedsVarName,
  normalizeVarKey
} from '@/config/commonStepCatalog'
import { ElMessage, ElMessageBox } from 'element-plus'

const MODULE_OPTIONS = ['清扫', '回充', '建图', '地图管理', '禁区', '语音交互', '固件升级', '账号登录', '其他']

const ASSERT_TYPES = new Set([
  'assert_text', 'assert_exists', 'assert_not_exists', 'assert_ocr', 'assert_toast', 'assert_http', 'assert_analytics',
  'assert_composite', 'check_anomaly', 'assert_process', 'clipboard_assert', 'assert_screen',
  'assert_key', 'assert_volume', 'assert_volume_change', 'assert_image', 'assert_cold_start',
  'assert_compare', 'assert_element_count', 'assert_attribute'
])

const route = useRoute()
const router = useRouter()
const taskId = computed(() => route.params.id || null)
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
const collapsedBlockIds = ref(new Set())
const showImportCommon = ref(false)
const importCommonNames = ref([])
const showCommonView = ref(false)
const commonViewLoading = ref(false)
const commonViewRow = ref(null)
const commonViewSteps = ref([])
const commonViewStack = ref([])
const commonViewTitle = computed(() =>
  commonViewRow.value?.name ? `查看公共步骤 · ${commonViewRow.value.name}` : '查看公共步骤'
)
const commonViewTimelineNodes = computed(() => buildCommonViewTimeline(commonViewSteps.value))

function buildCommonViewTimeline(steps) {
  const list = Array.isArray(steps) ? steps : []
  const nodes = []
  let i = 0
  let seq = 0
  while (i < list.length) {
    const step = list[i]
    if (!step || step.type === 'end_block') {
      i += 1
      continue
    }
    if (isFlowBlockStart(step)) {
      const end = findBlockEndIndex(list, i)
      // 有闭合块：子步骤为 (i, end)；无闭合块时 end 可能落在最后一个更深步骤上
      let innerEnd = end > i ? end : i + 1
      if (end > i && list[end]?.type === 'end_block') {
        // end 指向 end_block，slice 不含它
      } else if (end > i) {
        // 无 end_block：end 是最后一个子步骤，slice 需包含它
        innerEnd = end + 1
      }
      const childSteps = list.slice(i + 1, innerEnd)
      const children = buildCommonViewTimeline(childSteps)
      seq += 1
      nodes.push({
        key: `b-${i}-${step.id || seq}`,
        kind: 'block',
        index: seq,
        step,
        children
      })
      i = end > i ? end + 1 : i + 1
      continue
    }
    seq += 1
    nodes.push({
      key: `s-${i}-${step.id || seq}`,
      kind: 'step',
      index: seq,
      step
    })
    i += 1
  }
  return nodes
}

function timelineParamText(step) {
  if (!step) return ''
  if (step.type === 'wait' || step.type === 'manual_wait') {
    const ms = step.timeout ?? step.wait_ms ?? step.duration ?? step.ms
    if (ms != null && ms !== '') return `等待 ${ms} ms`
  }
  const summary = stepSummary(step)
  const locator = stepLocator(step)
  if (summary && locator && summary !== locator) return summary
  return summary || locator || ''
}

function timelineAssertText(step) {
  if (!step) return ''
  if (step.condition_kind === 'exists' || step.condition === 'exists' || step.condition === '控件存在') return '存在'
  if (step.condition_kind === 'not_exists' || step.condition === 'not_exists' || step.condition === '控件不存在') return '不存在'
  if (step.condition_kind === 'text_contains') return step.expected ? `包含 ${step.expected}` : '文本包含'
  if (step.condition_kind === 'var_equals' || step.condition_kind === 'var_not_equals') {
    return conditionLabel(step.condition_kind, step.condition, {
      var_name: step.var_name,
      expected: step.expected
    })
  }
  if (step.expected != null && String(step.expected).trim()) return String(step.expected)
  return ''
}

function flowElementName(step) {
  if (!step) return ''
  return (
    step.element_name ||
    step.target_name ||
    step.name ||
    formatStepTarget(step) ||
    (step.locator_value ? String(step.locator_value) : '') ||
    ''
  )
}

/** 判断块是否需要展示控件名 */
function judgeNeedsElement(step) {
  const kind = step?.condition_kind || ''
  if (kind === 'var_equals' || kind === 'var_not_equals' || kind === 'custom') return false
  if (kind === 'exists' || kind === 'not_exists' || kind === 'text_contains') return true
  const cond = String(step?.condition || '')
  if (cond.includes('变量') || cond.includes('{{')) return false
  return true
}

/** 块内首条「断言存在/不存在」可视为 if 条件的控件来源（历史写法） */
function findJudgeConditionAssert(parentItem) {
  const children = directBlockChildren(parentItem)
  const first = children[0]
  if (!first?.step) return null
  const t = first.step.type
  if (t === 'assert_exists' || t === 'assert_not_exists') return first
  return null
}

/** 表头断言控件：优先 if 自身，否则取块内首条断言步骤的控件 */
function judgeDisplayElement(parentItem) {
  const own = flowElementName(parentItem?.step)
  if (own) return own
  const assertChild = findJudgeConditionAssert(parentItem)
  return assertChild ? flowElementName(assertChild.step) : ''
}

function judgeDisplayAssertText(parentItem) {
  const own = timelineAssertText(parentItem?.step)
  const assertChild = findJudgeConditionAssert(parentItem)
  if (assertChild && !flowElementName(parentItem?.step)) {
    if (assertChild.step.type === 'assert_not_exists') return '不存在'
    if (assertChild.step.type === 'assert_exists') return '存在'
  }
  return own
}

/** 块内列表：若首条断言已提升到表头展示，则不再重复列出 */
function judgeBodyChildren(parentItem) {
  const children = directBlockChildren(parentItem)
  if (flowElementName(parentItem?.step)) return children
  const first = children[0]
  if (first && (first.step.type === 'assert_exists' || first.step.type === 'assert_not_exists')) {
    return children.slice(1)
  }
  return children
}

/** 判断块标题：明确「在判断什么」 */
function timelineJudgeActionText(step) {
  if (!step) return '条件判断'
  const kind = step.condition_kind || ''
  const cond = String(step.condition || '')
  if (kind === 'exists' || cond === 'exists' || cond === '控件存在') return '判断原生控件元素是否存在'
  if (kind === 'not_exists' || cond === 'not_exists' || cond === '控件不存在') return '判断原生控件元素是否不存在'
  if (kind === 'text_contains') return '判断原生控件文本是否包含'
  if (kind === 'var_equals') return '判断变量是否等于'
  if (kind === 'var_not_equals') return '判断变量是否不等于'
  if (kind === 'custom') return cond || '自定义条件判断'
  if (cond.includes('不存在')) return '判断原生控件元素是否不存在'
  if (cond.includes('存在')) return '判断原生控件元素是否存在'
  if (step.type === 'loop') return step.loop_mode === 'while' ? '循环判断条件' : '循环执行'
  return cond || '条件判断'
}

/** 嵌套子步骤展示片段：蓝动作 + 灰目标，对齐图一 */
function timelineChildParts(step) {
  if (!step) return []
  const t = step.type
  const el = flowElementName(step)
  const parts = []
  const action = (text) => parts.push({ kind: 'action', text })
  const param = (text) => { if (text) parts.push({ kind: 'param', text }) }
  const plain = (text) => { if (text) parts.push({ kind: 'text', text }) }

  if (t === 'click' || t === 'tap_xy' || t === 'long_press' || t === 'tap_ocr') {
    action('点击原生控件元素')
    param(el)
    return parts
  }
  if (t === 'input') {
    action('原生控件元素')
    param(el)
    action('输入文本')
    plain(step.text || '')
    return parts
  }
  if (t === 'clear_input') {
    action('清空输入')
    param(el || '当前输入框')
    return parts
  }
  if (t === 'wait' || t === 'manual_wait') {
    action('等待')
    param(stepSummary(step))
    return parts
  }
  if (t === 'assert_exists') {
    action('断言控件存在')
    param(el)
    return parts
  }
  if (t === 'assert_not_exists') {
    action('断言控件不存在')
    param(el)
    return parts
  }
  if (t === 'assert_text') {
    action('断言文本')
    param(el)
    plain(step.expected || '')
    return parts
  }
  if (t === 'launch') {
    action('启动')
    param(stepSummary(step))
    return parts
  }
  if (t === 'force_stop') {
    action('强制停止')
    param(stepSummary(step))
    return parts
  }
  if (t === 'invoke_common') {
    action('使用公共步骤')
    param(step.common_step || '')
    return parts
  }
  if (t === 'swipe') {
    action('滑动')
    param(stepSummary(step))
    return parts
  }
  action(stepTypeLabel(step))
  param(el || stepSummary(step))
  return parts.filter(p => p.text)
}

function timelineBlockTag(step) {
  const t = step?.type
  if (t === 'else_if' || t === 'elif') return 'else if'
  if (t === 'else') return 'else'
  if (t === 'loop') return 'while'
  return 'if'
}
const showPoolPicker = ref(false)
const poolKeyword = ref('')
const poolPageFilter = ref('')
const poolPickMode = ref('locator') // locator | tap | swipe_start | swipe_end
const poolBoundsOnly = ref(true)
const selectedPoolRows = ref([])
const selectedPoolRow = ref(null)
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
const dragStepRange = ref(null) // { start, end }
const editingIndex = ref(null)
const insertAtIndex = ref(null)
const rightTab = ref('list')
const selectedCatalogId = ref('ctrl.android.click')
const treeExpandedKeys = ref(['ctrl', 'ctrl.android'])
const stepAddPanelRef = ref(null)
const autoSaveHint = ref('')
const locatorHint = ref('')
const basicInfoRef = ref(null)
const stepsAreaRef = ref(null)
const justSaved = ref(false)
const showFolderDialog = ref(false)
const folderSaving = ref(false)
const folderForm = reactive({ name: '', parent_id: null })
let stepSeq = 1
let autoSaveTimer = null
let dirty = false
let lastSavedSnapshot = ''
let justSavedTimer = null

const FLOW_STAGES = [
  { key: 'basic', label: '基础信息填写' },
  { key: 'steps', label: '步骤可视化编排' },
  { key: 'assert', label: '断言配置' },
  { key: 'debug', label: '调试执行' },
  { key: 'save', label: '保存定稿' }
]
const FLOW_STAGE_ORDER = FLOW_STAGES.map(s => s.key)

const hasAssertStep = computed(() => steps.value.some(s => ASSERT_TYPES.has(s.type)))

const flowStage = computed(() => {
  if (justSaved.value) return 'save'
  if (!(meta.name || '').trim()) return 'basic'
  if (!steps.value.length) return 'steps'
  if (!hasAssertStep.value) return 'assert'
  if (taskId.value) return 'debug'
  return 'assert'
})

function goToFlowStage(key) {
  if (key === 'basic') {
    basicInfoRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  if (key === 'steps') {
    rightTab.value = 'list'
    nextTick(() => stepsAreaRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
    return
  }
  if (key === 'assert') {
    rightTab.value = 'list'
    nextTick(() => {
      stepsAreaRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      if (!steps.value.length) focusAddPanel('assert_exists')
    })
    return
  }
  if (key === 'debug') {
    document.querySelector('.btn-debug')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    return
  }
  if (key === 'save') {
    document.querySelector('.btn-save')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }
}

function onMoreCommand(cmd) {
  if (cmd === 'author') goAuthorWorkspace()
  else if (cmd === 'versions') openVersionHistory()
  else if (cmd === 'comments') openComments()
  else if (cmd === 'back') router.back()
}

function openFolderDialog() {
  folderForm.name = ''
  folderForm.parent_id = meta.folder_id || null
  showFolderDialog.value = true
}

async function saveFolder() {
  if (!folderForm.name?.trim()) {
    ElMessage.warning('请输入目录名')
    return
  }
  folderSaving.value = true
  try {
    const res = await caseApi.createFolder({
      name: folderForm.name.trim(),
      parent_id: folderForm.parent_id || null
    })
    await loadFolders()
    const newId = res?.data?.id
    if (newId) meta.folder_id = newId
    showFolderDialog.value = false
    ElMessage.success('目录已创建')
  } catch (e) {
    ElMessage.error(e?.message || '创建目录失败')
  } finally {
    folderSaving.value = false
  }
}

function openPoolFromEmpty() {
  rightTab.value = 'add'
  openPoolPicker()
}

function addAssertAfter(idx) {
  const src = steps.value[idx]
  if (!src) return
  const assertStep = {
    id: stepSeq++,
    enabled: true,
    disable_reason: '',
    disable_mode: '',
    ...newStepDefaults(),
    type: 'assert_exists',
    element_name: src.element_name || '',
    display_name: src.display_name || src.element_name || '',
    locator_type: src.locator_type || '',
    locator_value: src.locator_value || '',
    catalog_id: 'ctrl.android.exists',
    remark: ''
  }
  steps.value.splice(idx + 1, 0, assertStep)
  editingIndex.value = idx + 1
  Object.assign(newStep, newStepDefaults(), JSON.parse(JSON.stringify(assertStep)))
  selectedCatalogId.value = 'ctrl.android.exists'
  rightTab.value = 'add'
  ElMessage.success('已插入断言步骤，请完善断言条件')
}

function markJustSaved() {
  justSaved.value = true
  if (justSavedTimer) clearTimeout(justSavedTimer)
  justSavedTimer = setTimeout(() => { justSaved.value = false }, 4000)
}

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

/** 清除历史自动填入 / 控件流默认描述 */
const AUTO_REMARK_RE = /^(来自控件库|来自控件拾取|断言[：:]|结束\s*(if|else if|else|while)|否则分支)$/
function normalizeStepRemark(remark) {
  const t = String(remark || '').trim()
  if (!t) return ''
  if (AUTO_REMARK_RE.test(t)) return ''
  return String(remark || '')
}
function mapLoadedStep(s) {
  const remark = normalizeStepRemark(s.remark)
  // 截图：历史把名称写在 remark/描述里 → 迁到 element_name，描述清空
  let elementName = s.element_name
  let nextRemark = remark
  if (s.type === 'screenshot') {
    const name = String(s.element_name || s.name || '').trim()
    const fromRemark = String(s.remark || '').trim()
    if (!name && fromRemark && !AUTO_REMARK_RE.test(fromRemark)) {
      elementName = fromRemark
      nextRemark = ''
    } else if (name && fromRemark && name === fromRemark) {
      nextRemark = ''
    }
  }
  return {
    ...s,
    id: s.id != null ? s.id : stepSeq++,
    enabled: s.enabled !== false,
    disable_reason: s.disable_reason || '',
    element_name: elementName ?? s.element_name,
    remark: nextRemark,
    on_fail: normalizeOnFail(s.on_fail),
    logic_process: s.logic_process || 'none'
  }
}

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
  swipe_start_name: '',
  swipe_end_name: '',
  swipe_start_pool_id: null,
  swipe_end_pool_id: null,
  pool_id: null,
  duration_ms: 300,
  common_step: '', input_params_json: '', check_types: 'all', mode: 'disk',
  case_id: null, case_name: '',
  method: 'GET', url: '', expected_status: 200, body_contains: '', timeout: 5,
  event_name: '', props_json: '{}', verify_url: '',
  conditions: '[{"type":"text","value":"首页"}]',
  enabled: true, disable_reason: '', disable_mode: '',
  retry_count: 0, on_fail: 'fail', logic_process: 'none', key: 'back', template_id: null, template_path: '', threshold: 0.85,
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
const hasBlockSteps = computed(() =>
  steps.value.some(s => isFlowBlockStart(s))
)

const canSaveToPool = computed(() => {
  const name = (newStep.element_name || '').trim()
  const loc = (newStep.locator_value || '').trim()
  return !!(name && (loc || newStep.locator_type))
})

const poolPageOptions = computed(() => {
  const set = new Set()
  for (const p of poolItems.value || []) {
    const name = String(p.page_name || '').trim()
    if (name) set.add(name)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const filteredPoolItems = computed(() => {
  let list = poolItems.value || []
  if (isCoordPoolMode.value && poolBoundsOnly.value) {
    list = list.filter(isCoordinateControl)
  }
  const page = poolPageFilter.value.trim()
  if (page) {
    list = list.filter(p => String(p.page_name || '').trim() === page)
  }
  const k = poolKeyword.value.trim().toLowerCase()
  if (!k) return list
  return list.filter(p => {
    const hay = [p.element_name, p.page_name, p.locator_type, p.locator_value, p.device_element_value]
      .join(' ').toLowerCase()
    return hay.includes(k)
  })
})

const isCoordPoolMode = computed(() =>
  ['tap', 'swipe_start', 'swipe_end'].includes(poolPickMode.value)
)

const poolPickerTitle = computed(() => {
  if (poolPickMode.value === 'swipe_start') return '选择起点坐标控件'
  if (poolPickMode.value === 'swipe_end') return '选择终点坐标控件'
  if (poolPickMode.value === 'tap') return '选择坐标控件'
  return '从控件库选择控件'
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
  const list = steps.value
  const depths = computeStepDepths(list)
  const searching = !!stepKeyword.value.trim()
  const hidden = searching ? new Set() : computeCollapsedHiddenIndices(list, collapsedBlockIds.value)
  return filteredSteps.value.map(step => {
    const index = list.findIndex(s => s === step || (s.id != null && s.id === step.id))
    const key = blockCollapseKey(step, index)
    const collapsedCount = isFlowBlockStart(step) && collapsedBlockIds.value.has(key)
      ? Math.max(0, findBlockEndIndex(list, index) - index)
      : 0
    return {
      step,
      index,
      collapseKey: key,
      depth: index >= 0 ? depths[index] : 0,
      collapsedCount
    }
  }).filter(x => x.index >= 0 && !hidden.has(x.index))
})

/** 顶层步骤：块内子步骤 / end_block 不单独占一行，改嵌在判断卡片内；编号不含块内步骤 */
const topLevelStepItems = computed(() => {
  const nested = computeIndicesInsideBlocks(steps.value)
  let seq = 0
  return filteredStepItems.value
    .filter(item => {
      if (item.step.type === 'end_block') return false
      return !nested.has(item.index)
    })
    .map(item => ({
      ...item,
      displayNo: ++seq
    }))
})

/** 总步骤数（不含判断/循环块内步骤与 end_block） */
const topLevelStepCount = computed(() => {
  const nested = computeIndicesInsideBlocks(steps.value)
  let n = 0
  steps.value.forEach((s, i) => {
    if (!s || s.type === 'end_block') return
    if (nested.has(i)) return
    n += 1
  })
  return n
})

/** 按 branch/end_block 结构取直接子步骤（不依赖 indent，避免 indent:0 导致嵌套丢失） */
function directBlockChildren(parentItem) {
  const list = steps.value
  const start = parentItem?.index
  if (start == null || start < 0) return []
  const end = findBlockEndIndex(list, start)
  if (end <= start) return []
  const out = []
  let i = start + 1
  while (i < end) {
    const s = list[i]
    if (!s || s.type === 'end_block') {
      i += 1
      continue
    }
    out.push({
      step: s,
      index: i,
      depth: (parentItem.depth || 0) + 1,
      collapseKey: blockCollapseKey(s, i)
    })
    if (isFlowBlockStart(s)) {
      const childEnd = findBlockEndIndex(list, i)
      i = childEnd > i ? childEnd + 1 : i + 1
    } else {
      i += 1
    }
  }
  return out
}

function computeIndicesInsideBlocks(list) {
  const nested = new Set()
  for (let i = 0; i < list.length; i++) {
    if (!isFlowBlockStart(list[i])) continue
    const end = findBlockEndIndex(list, i)
    if (end <= i) continue
    for (let j = i + 1; j <= end; j++) nested.add(j)
  }
  return nested
}

function findBlockEndIndex(list, startIdx) {
  const start = list[startIdx]
  if (!start || !isFlowBlockStart(start)) return -1
  let depth = 1
  for (let i = startIdx + 1; i < list.length; i++) {
    const t = list[i].type
    if (isFlowBlockStartType(t)) depth += 1
    else if (t === 'end_block') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  // 无闭合 end_block 时：按缩进隐藏后续更深层级，直到回到同级
  const depths = computeStepDepths(list)
  const base = depths[startIdx] ?? 0
  let last = startIdx
  for (let i = startIdx + 1; i < list.length; i++) {
    const d = depths[i] ?? 0
    if (d <= base) break
    last = i
  }
  return last > startIdx ? last : -1
}

function isFlowBlockStartType(t) {
  return t === 'branch' || t === 'loop' || t === 'else_if' || t === 'elif' || t === 'else'
}

function isFlowBlockStart(step) {
  return !!step && isFlowBlockStartType(step.type) && step.branch_mode !== 'try_catch'
}

function computeCollapsedHiddenIndices(list, collapsedIds) {
  const hidden = new Set()
  if (!collapsedIds?.size) return hidden
  for (let i = 0; i < list.length; i++) {
    const s = list[i]
    if (!isFlowBlockStart(s) || !collapsedIds.has(blockCollapseKey(s, i))) continue
    const end = findBlockEndIndex(list, i)
    if (end <= i) continue
    for (let j = i + 1; j <= end; j++) hidden.add(j)
  }
  return hidden
}

function blockCollapseKey(step, index) {
  return step?.id != null ? step.id : `idx:${index}`
}

function toggleBlockCollapse(stepId) {
  if (stepId == null || stepId === '') return
  const next = new Set(collapsedBlockIds.value)
  if (next.has(stepId)) next.delete(stepId)
  else next.add(stepId)
  collapsedBlockIds.value = next
}

function expandAllBlocks() {
  collapsedBlockIds.value = new Set()
}

function collapseAllBlocks() {
  const next = new Set()
  steps.value.forEach((s, i) => {
    if (isFlowBlockStart(s)) next.add(blockCollapseKey(s, i))
  })
  collapsedBlockIds.value = next
}

function computeStepDepths(list) {
  // 以 branch/end_block 结构为准；显式 indent 仅在更大时加深（用户手动多缩进）
  const depths = []
  let depth = 0
  for (const s of list) {
    if (s.type === 'end_block') {
      depth = Math.max(0, depth - 1)
      depths.push(depth)
      continue
    }
    const structural = depth
    const indent = typeof s.indent === 'number' ? s.indent : structural
    const d = Math.max(structural, Math.min(6, indent))
    depths.push(d)
    if (isFlowBlockStartType(s.type) && s.branch_mode !== 'try_catch') {
      depth = Math.min(6, structural + 1)
    }
  }
  return depths
}

function isFlowHeaderStep(step) {
  return isFlowBlockStart(step)
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
  install_apk: '安装包', branch: 'if判断', else_if: 'else if', else: 'else', loop: 'while循环', end_block: '结束块',
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
const APP_TYPES = new Set([
  'launch', 'clear_cache', 'force_stop', 'install_apk', 'switch_context', 'revoke_permissions',
  'press_key', 'clipboard_set', 'wake_screen', 'lock_screen', 'dismiss_popup', 'custom_script',
  'invoke_common', 'invoke_case', 'branch', 'else_if', 'else', 'loop', 'end_block'
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
  if (APP_TYPES.has(type) || type === 'branch' || type === 'else_if' || type === 'else' || type === 'loop') return 'tone-app'
  return 'tone-default'
}

function stepTagType(type) {
  if (WAIT_TYPES.has(type)) return 'info'
  if (CLICK_TYPES.has(type)) return 'primary'
  if (ASSERT_TYPES.has(type)) return 'success'
  if (APP_TYPES.has(type)) return 'warning'
  if (type === 'branch' || type === 'else_if' || type === 'else' || type === 'loop') return 'warning'
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
    case 'screenshot': return step.element_name || step.name || step.remark || step.save_path || ''
    case 'set_relative_time': {
      const mins = step.offset_minutes ?? 5
      return `+${mins}分钟 → TIME_HM${step.confirm ? ' · 并点确定' : ''}`
    }
    case 'launch': return step.app_package || meta.app_package
    case 'install_apk': return step.app_package || '安装包'
    case 'swipe': {
      if (step.swipe_start_name && step.swipe_end_name) {
        return `${step.swipe_start_name} → ${step.swipe_end_name}`
      }
      return target || `${step.x1},${step.y1}→${step.x2},${step.y2}`
    }
    case 'assert_text': return `${step.element_name || ''} = ${step.expected}`
    case 'invoke_common': return step.common_step || '(未选)'
    case 'invoke_case': return step.case_name || (step.case_id ? `#${step.case_id}` : '(未选)')
    case 'custom_script': {
      const lang = (step.script_lang || step.language || 'python').toUpperCase()
      const name = step.element_name || step.display_name || ''
      const code = step.script_code || step.script || ''
      const lines = String(code).split('\n').filter(l => l.trim()).length
      if (name) return lines ? `${lang} · ${name}（${lines} 行）` : `${lang} · ${name}（未填脚本）`
      if (!lines) return `${lang} · 未填写脚本内容`
      return `${lang} · ${lines} 行`
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
    case 'branch': {
      if (step.condition_kind === 'var_equals' || step.condition_kind === 'var_not_equals') {
        return conditionLabel(step.condition_kind, step.condition, {
          var_name: step.var_name,
          expected: step.expected
        })
      }
      return step.condition || 'if 条件'
    }
    case 'else_if':
    case 'elif': {
      if (step.condition_kind === 'var_equals' || step.condition_kind === 'var_not_equals') {
        return conditionLabel(step.condition_kind, step.condition, {
          var_name: step.var_name,
          expected: step.expected
        })
      }
      return step.condition || 'else if 条件'
    }
    case 'else': return step.remark || '否则分支'
    case 'loop': {
      if (step.loop_mode === 'while') return step.condition || `while · 最多 ${step.loop_count || 10} 次`
      return `循环 ${step.loop_count || 1} 次`
    }
    case 'end_block': {
      if (step.remark) return step.remark
      if (step.block_type === 'loop') return '结束 while'
      return '结束分支'
    }
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
  'clear_input': 'ctrl.android.clear',
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
  'wake_screen': 'device.screen.unlock',
  'branch': 'flow.if',
  'else_if': 'flow.else_if',
  'elif': 'flow.else_if',
  'else': 'flow.else',
  'end_block': 'flow.end_if'
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
    ...(leaf?.extras || {}),
    ...JSON.parse(JSON.stringify(newStep)),
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
  if (step.condition_kind) {
    if (conditionNeedsVarName(step.condition_kind)) {
      step.var_name = normalizeVarKey(step.var_name)
    }
    if (step.condition_kind !== 'custom') {
      step.condition = conditionLabel(step.condition_kind, step.condition, {
        var_name: step.var_name,
        expected: step.expected
      })
    }
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
  const coordsMode = leaf.needsCoords
    || (fields.some(k => ['x', 'y'].includes(k)) ? 'tap' : '')
    || (fields.some(k => ['swipe_start_name', 'x1'].includes(k)) ? 'swipe' : '')

  if (coordsMode === 'tap') {
    if (!(newStep.element_name || '').trim()) {
      ElMessage.warning('请从控件库选择坐标控件')
      return false
    }
  } else if (coordsMode === 'swipe') {
    if (!(newStep.swipe_start_name || '').trim() || !(newStep.swipe_end_name || '').trim()) {
      ElMessage.warning('请从控件库分别选择起点、终点坐标控件')
      return false
    }
  } else if (fields.includes('condition_kind')) {
    const kind = newStep.condition_kind || 'exists'
    if (conditionNeedsLocator(kind)) {
      if (!(newStep.locator_value || '').trim() || !(newStep.element_name || '').trim()) {
        ElMessage.warning('请从控件库选择目标控件')
        return false
      }
    }
    if (conditionNeedsVarName(kind) && !normalizeVarKey(newStep.var_name)) {
      ElMessage.warning('请填写变量名，例如 product_id')
      return false
    }
    if (conditionNeedsExpected(kind) && !(newStep.expected || '').trim()) {
      ElMessage.warning(conditionNeedsVarName(kind) ? '请填写期望值，例如 AX17' : '请填写期望文案')
      return false
    }
    if (kind === 'custom' && !(newStep.condition || '').trim()) {
      ElMessage.warning('请填写自定义判断条件')
      return false
    }
  } else if (leaf.needsLocator || fields.includes('locator_value')) {
    if (!(newStep.locator_value || '').trim()) {
      ElMessage.warning('请从控件库选择目标控件')
      return false
    }
    if (!(newStep.element_name || '').trim()) {
      ElMessage.warning('请从控件库选择目标控件')
      return false
    }
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
    // 编辑单步时不改结构；逻辑块请用「逻辑处理」在新增时生成
    delete step.logic_process
    const id = steps.value[editingIndex.value]?.id
    step.id = id ?? step.id
    steps.value[editingIndex.value] = step
    editingIndex.value = null
    insertAtIndex.value = null
    resetNewStep()
    ElMessage.success('步骤已更新')
    rightTab.value = 'add'
    return
  }
  const logic = newStep.logic_process || 'none'
  if ((logic === 'else_if' || logic === 'else') && findNearestChainEndIndex() < 0) {
    ElMessage.warning('当前没有可衔接的 if 分支，请先添加「逻辑处理 = if」的步骤')
    return
  }
  addStep()
  rightTab.value = 'add'
}

/** 从动作步骤提取 if/while 条件（断言 / 带定位控件优先） */
function extractConditionFromBody(body) {
  if (!body) {
    return { condition_kind: 'custom', condition: 'true', timeout: 5 }
  }
  if (body.type === 'assert_exists' || body.type === 'assert_not_exists') {
    const not = body.type === 'assert_not_exists'
    return {
      condition_kind: not ? 'not_exists' : 'exists',
      condition: not ? '控件不存在' : '控件存在',
      element_name: body.element_name || '',
      display_name: body.display_name || body.element_name || '',
      locator_type: body.locator_type || '',
      locator_value: body.locator_value || '',
      timeout: body.timeout || body.wait_timeout || 5
    }
  }
  if ((body.locator_value || '').trim() && (body.element_name || '').trim()) {
    return {
      condition_kind: 'exists',
      condition: '控件存在',
      element_name: body.element_name,
      display_name: body.display_name || body.element_name,
      locator_type: body.locator_type || '',
      locator_value: body.locator_value,
      timeout: body.wait_timeout || body.timeout || 5
    }
  }
  if (body.condition_kind) {
    return {
      condition_kind: body.condition_kind,
      condition: body.condition || conditionLabel(body.condition_kind, '', {
        var_name: body.var_name,
        expected: body.expected
      }),
      element_name: body.element_name || '',
      display_name: body.display_name || '',
      locator_type: body.locator_type || '',
      locator_value: body.locator_value || '',
      var_name: body.var_name || '',
      expected: body.expected || '',
      timeout: body.timeout || 5
    }
  }
  return { condition_kind: 'custom', condition: 'true', timeout: 5 }
}

function isAssertAsConditionBody(body) {
  return body && (body.type === 'assert_exists' || body.type === 'assert_not_exists')
}

function mkControlStep(type, extra = {}) {
  return {
    id: stepSeq++,
    enabled: true,
    disable_reason: '',
    disable_mode: '',
    ...newStepDefaults(),
    type,
    logic_process: 'none',
    ...extra
  }
}

/**
 * 按「逻辑处理」生成真实 if / else if / else / while 块（与脚本生成器链式跳过约定一致）。
 * @returns {boolean} 是否已按逻辑块处理（true 则调用方勿再普通插入）
 */
function addStepWithLogicProcess(bodyStep) {
  const logic = bodyStep.logic_process || 'none'
  if (!logic || logic === 'none') return false
  // 本身已是流程头：走原有块结束逻辑
  if (isFlowBlockStart(bodyStep)) return false

  const cond = extractConditionFromBody(bodyStep)
  const asCondOnly = isAssertAsConditionBody(bodyStep)
    && (logic === 'if' || logic === 'while' || logic === 'else_if')

  if (logic === 'else_if' || logic === 'else') {
    const endIdx = findNearestChainEndIndex()
    if (endIdx < 0) {
      ElMessage.warning(logic === 'else'
        ? '请先添加 if 判断块，再添加 else'
        : '请先添加 if 判断块，再添加 else if')
      return true // 已处理（失败提示），阻止再当普通步骤插入
    }
    if (logic === 'else') {
      let start = null
      for (let i = endIdx - 1; i >= 0; i--) {
        if (isFlowBlockStart(steps.value[i]) && findBlockEndIndex(steps.value, i) === endIdx) {
          start = steps.value[i]
          break
        }
      }
      if (start?.type === 'else') {
        ElMessage.warning('else 已是链路末尾，不能再追加')
        return true
      }
    }
    const depths = computeStepDepths(steps.value)
    const indent = depths[endIdx] ?? 0
    const header = mkControlStep(logic === 'else' ? 'else' : 'else_if', {
      indent,
      ...(logic === 'else' ? {} : cond)
    })
    const end = mkControlStep('end_block', { block_type: 'branch', remark: '', indent })
    const chunk = [header]
    if (!asCondOnly) {
      const body = {
        ...bodyStep,
        id: stepSeq++,
        logic_process: 'none',
        indent: Math.min(6, indent + 1)
      }
      chunk.push(body)
    }
    chunk.push(end)
    steps.value.splice(endIdx + 1, 0, ...chunk)
    insertAtIndex.value = null
    nextTick(() => {
      const next = new Set(collapsedBlockIds.value)
      next.add(blockCollapseKey(header, endIdx + 1))
      collapsedBlockIds.value = next
    })
    ElMessage.success(logic === 'else'
      ? (asCondOnly ? '已接入 else 块（条件类步骤已并入逻辑头）' : '已接入 else 块，动作步骤已放入块内')
      : (asCondOnly ? '已接入 else if 块（断言已作为判断条件）' : '已接入 else if 块，动作步骤已放入块内'))
    return true
  }

  if (logic === 'if' || logic === 'while') {
    const at = insertAtIndex.value
    const depths = computeStepDepths(steps.value)
    let indent = 0
    if (at != null && at >= 0 && at <= steps.value.length) {
      indent = suggestIndentForInsert(at)
    } else if (depths.length) {
      indent = depths[depths.length - 1] || 0
    }
    const isLoop = logic === 'while'
    const header = mkControlStep(isLoop ? 'loop' : 'branch', {
      indent,
      ...cond,
      ...(isLoop ? { loop_mode: 'while', loop_count: 10 } : {})
    })
    const end = mkControlStep('end_block', {
      block_type: isLoop ? 'loop' : 'branch',
      remark: '',
      indent
    })
    const chunk = [header]
    if (!asCondOnly) {
      chunk.push({
        ...bodyStep,
        id: stepSeq++,
        logic_process: 'none',
        indent: Math.min(6, indent + 1)
      })
    }
    chunk.push(end)
    let insertPos
    if (at != null && at >= 0 && at <= steps.value.length) {
      steps.value.splice(at, 0, ...chunk)
      insertPos = at
      insertAtIndex.value = at + chunk.length
    } else {
      insertPos = steps.value.length
      steps.value.push(...chunk)
      insertAtIndex.value = null
    }
    nextTick(() => {
      const next = new Set(collapsedBlockIds.value)
      next.add(blockCollapseKey(header, insertPos))
      collapsedBlockIds.value = next
    })
    ElMessage.success(isLoop
      ? (asCondOnly ? '已创建 while 循环块（断言已作为循环条件）' : '已创建 while 循环块，动作步骤已放入循环体内')
      : (asCondOnly ? '已创建 if 判断块（断言已作为判断条件）' : '已创建 if 判断块，动作步骤已放入成立分支内'))
    return true
  }

  return false
}

function addStep() {
  const step = buildStepFromForm()
  if (!step) return

  // 逻辑处理：生成真实 if / else if / else / while 链路（脚本可执行）
  if (addStepWithLogicProcess(step)) {
    resetNewStep(step.type)
    if (selectedCatalogId.value) {
      const leaf = getCatalogLeaf(selectedCatalogId.value)
      if (leaf && stepAddPanelRef.value?.applyLeaf) {
        stepAddPanelRef.value.applyLeaf(leaf, { keepCurrent: false, openDialog: false })
      }
    }
    return
  }

  const at = insertAtIndex.value
  let insertedAt
  if (at != null && at >= 0 && at <= steps.value.length) {
    if (step.indent == null) step.indent = suggestIndentForInsert(at)
    steps.value.splice(at, 0, step)
    insertedAt = at
    insertAtIndex.value = at + 1
    ElMessage.success(`已插入到第 ${at + 1} 步`)
  } else {
    steps.value.push(step)
    insertedAt = steps.value.length - 1
    ElMessage.success('步骤已添加')
  }
  // 直接选流程指令时：if / else if / else / while 各自带独立结束块
  if (isFlowBlockStart(step)) {
    const endAt = insertedAt + 1
    const endStep = mkControlStep('end_block', {
      block_type: step.type === 'loop' ? 'loop' : 'branch',
      remark: '',
      indent: typeof step.indent === 'number' ? step.indent : 0
    })
    steps.value.splice(endAt, 0, endStep)
    if (insertAtIndex.value != null) insertAtIndex.value = endAt + 1
  }
  resetNewStep(step.type)
  if (selectedCatalogId.value) {
    const leaf = getCatalogLeaf(selectedCatalogId.value)
    if (leaf && stepAddPanelRef.value?.applyLeaf) {
      stepAddPanelRef.value.applyLeaf(leaf, { keepCurrent: false, openDialog: false })
    }
  }
}

function suggestIndentForInsert(at) {
  const list = steps.value
  if (!list.length) return 0
  // 插在 end_block 前：跟前一条子步骤同缩进；插在 branch/loop/else 后：块内缩进
  const prev = list[at - 1]
  const next = list[at]
  if (prev && (prev.type === 'branch' || prev.type === 'loop' || prev.type === 'else_if' || prev.type === 'elif' || prev.type === 'else')) {
    const base = typeof prev.indent === 'number' ? prev.indent : 0
    return Math.min(6, base + 1)
  }
  if (next?.type === 'end_block' && prev) {
    return typeof prev.indent === 'number' ? prev.indent : 1
  }
  if (prev && typeof prev.indent === 'number') return prev.indent
  const depths = computeStepDepths(list)
  if (at > 0 && depths[at - 1] != null) return depths[at - 1]
  return 0
}

function beginInsertAt(idx, mode = 'after') {
  editingIndex.value = null
  const at = mode === 'before' ? idx : idx + 1
  insertAtIndex.value = Math.max(0, Math.min(steps.value.length, at))
  resetNewStep()
  rightTab.value = 'add'
  ElMessage.info(`将插入到第 ${insertAtIndex.value + 1} 步位置（可连续添加）`)
  nextTick(() => {
    document.querySelector('.workspace-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    stepAddPanelRef.value?.openStepDialog?.()
  })
}

function editStep(idx) {
  const step = steps.value[idx]
  if (!step) return
  insertAtIndex.value = null
  editingIndex.value = idx
  Object.assign(newStep, newStepDefaults(), JSON.parse(JSON.stringify(step)), {
    input_params_json: step.input_params ? JSON.stringify(step.input_params, null, 2) : (step.input_params_json || '')
  })
  const catalogId = resolveCatalogId(step)
  if (catalogId) selectedCatalogId.value = catalogId
  rightTab.value = 'add'
  nextTick(() => {
    document.querySelector('.workspace-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    stepAddPanelRef.value?.openStepDialog?.()
  })
}

function cancelEdit() {
  editingIndex.value = null
  insertAtIndex.value = null
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

function onOpenPool(mode = 'locator') {
  openPoolPicker(typeof mode === 'string' ? mode : 'locator')
}

function isCoordinateControl(row) {
  const t = String(row?.locator_type || '').toLowerCase()
  if (['bounds', 'coordinate', 'xy', 'screen_ratio'].includes(t)) return true
  return /\[\d+,\d+\]\[\d+,\d+\]/.test(String(row?.locator_value || ''))
}

function parseBounds(raw) {
  const s = String(raw || '').trim()
  const m = s.match(/\[(\d+)\s*,\s*(\d+)\]\s*\[(\d+)\s*,\s*(\d+)\]/)
  if (m) {
    return {
      x1: Number(m[1]),
      y1: Number(m[2]),
      x2: Number(m[3]),
      y2: Number(m[4])
    }
  }
  const pair = s.match(/^(\d+)\s*,\s*(\d+)$/)
  if (pair) {
    const x = Number(pair[1])
    const y = Number(pair[2])
    return { x1: x, y1: y, x2: x, y2: y }
  }
  return null
}

function pointFromBounds(bounds) {
  return {
    x: Math.round((bounds.x1 + bounds.x2) / 2),
    y: Math.round((bounds.y1 + bounds.y2) / 2)
  }
}

function poolLocatorTypeLabel(type) {
  const map = {
    id: 'ID',
    resource_id: 'ID',
    accessibility: '文案',
    content_desc: '文案',
    text: '文本',
    xpath: 'xpath',
    bounds: '坐标定位',
    coordinate: '坐标定位',
    xy: '坐标定位'
  }
  return map[type] || type || '—'
}

async function openPoolPicker(mode = 'locator') {
  poolPickMode.value = mode || 'locator'
  poolKeyword.value = ''
  poolPageFilter.value = ''
  selectedPoolRows.value = []
  selectedPoolRow.value = null
  poolBoundsOnly.value = isCoordPoolMode.value
  showPoolPicker.value = true
  if (poolItems.value.length) return
  try {
    const res = await controlApi.listPool({ page: 1, page_size: 500, status: 'active' })
    poolItems.value = res.data?.list || res.data?.items || res.data || []
  } catch {
    poolItems.value = []
    ElMessage.warning('加载控件库失败')
  }
}

function onPoolPickerClosed() {
  selectedPoolRows.value = []
  selectedPoolRow.value = null
}

function onPoolRowClick(row) {
  if (!isCoordPoolMode.value) return
  selectedPoolRow.value = row || null
  nextTick(() => poolTableRef.value?.setCurrentRow?.(row || undefined))
}

function poolRowClassName({ row }) {
  return selectedPoolRow.value?.id === row?.id ? 'is-pool-selected' : ''
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
  onLocatorFieldChange()
}

function fillTapFromPool(row) {
  const bounds = parseBounds(row.locator_value)
  if (!bounds) {
    ElMessage.warning('该控件不是有效坐标定位，请选择 bounds / x,y 类型')
    return false
  }
  const pt = pointFromBounds(bounds)
  newStep.x = pt.x
  newStep.y = pt.y
  newStep.element_name = row.element_name || ''
  newStep.display_name = row.element_name || ''
  newStep.locator_type = row.locator_type || 'bounds'
  newStep.locator_value = row.locator_value || ''
  newStep.pool_id = row.id
  return true
}

function fillSwipePointFromPool(row, which) {
  const bounds = parseBounds(row.locator_value)
  if (!bounds) {
    ElMessage.warning('该控件不是有效坐标定位，请选择 bounds / x,y 类型')
    return false
  }
  const pt = pointFromBounds(bounds)
  if (which === 'start') {
    newStep.x1 = pt.x
    newStep.y1 = pt.y
    newStep.swipe_start_name = row.element_name || ''
    newStep.swipe_start_pool_id = row.id
  } else {
    newStep.x2 = pt.x
    newStep.y2 = pt.y
    newStep.swipe_end_name = row.element_name || ''
    newStep.swipe_end_pool_id = row.id
  }
  return true
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
    remark: ''
  }
}

function confirmPoolSelection() {
  if (isCoordPoolMode.value) {
    const row = selectedPoolRow.value
    if (!row) {
      ElMessage.warning('请先选择一条控件')
      return
    }
    const mode = poolPickMode.value
    let ok = true
    if (mode === 'tap') ok = fillTapFromPool(row)
    else if (mode === 'swipe_start') ok = fillSwipePointFromPool(row, 'start')
    else if (mode === 'swipe_end') ok = fillSwipePointFromPool(row, 'end')
    if (!ok) return
    showPoolPicker.value = false
    ElMessage.success(`已选用控件「${row.element_name || '未命名'}」`)
    return
  }

  const rows = selectedPoolRows.value
  if (!rows.length) {
    ElMessage.warning('请先勾选控件')
    return
  }
  if (rows.length === 1) {
    // 单选：回填到当前步骤表单
    if (!['click', 'input', 'clear_input', 'assert_text', 'assert_exists', 'assert_not_exists', 'assert_ocr', 'tap_ocr', 'wait', 'long_press', 'get_text', 'scroll_to_element', 'assert_element_count', 'assert_attribute'].includes(newStep.type)) {
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
  quickAddStep('branch', {
    condition: '控件存在',
    condition_kind: 'exists',
    timeout: 5,
    branch_true: '执行成立分支',
    branch_false: '执行否则分支',
    indent: base
  })
  quickAddStep('end_block', { block_type: 'branch', remark: '', indent: base })
  nextTick(() => collapseAllBlocks())
  ElMessage.info('已插入独立 if 块。条件不成立时，在该结束块之后紧挨添加 else if / else 块')
}

/** 找到可衔接的最近 if / else if 结束块下标（else 之后不能再接） */
function findNearestChainEndIndex() {
  const list = steps.value
  let from = list.length - 1
  if (insertAtIndex.value != null) {
    from = Math.min(Math.max(0, insertAtIndex.value), list.length - 1)
  }
  const scan = (start) => {
    for (let i = start; i >= 0; i--) {
      const s = list[i]
      if ((s.type === 'branch' && s.branch_mode !== 'try_catch') || s.type === 'else_if' || s.type === 'elif') {
        const end = findBlockEndIndex(list, i)
        if (end > i) return end
      }
    }
    return -1
  }
  const hit = scan(from)
  if (hit >= 0) return hit
  return scan(list.length - 1)
}

function insertIndependentBlockAfter(endIdx, headerType, headerExtra) {
  const depths = computeStepDepths(steps.value)
  const indent = depths[endIdx] ?? 0
  const mk = (type, extra = {}) => ({
    id: stepSeq++,
    enabled: true,
    disable_reason: '',
    disable_mode: '',
    ...newStepDefaults(),
    type,
    indent,
    ...extra
  })
  const header = mk(headerType, headerExtra)
  const end = mk('end_block', { block_type: 'branch', remark: '' })
  steps.value.splice(endIdx + 1, 0, header, end)
  nextTick(() => {
    const next = new Set(collapsedBlockIds.value)
    next.add(blockCollapseKey(header, endIdx + 1))
    collapsedBlockIds.value = next
  })
  return header
}

function addElseIfStep() {
  const endIdx = findNearestChainEndIndex()
  if (endIdx < 0) {
    ElMessage.warning('请先添加 if 判断块，再在其结束块之后添加 else if')
    return
  }
  insertIndependentBlockAfter(endIdx, 'else_if', {
    condition: '控件存在',
    condition_kind: 'exists',
    timeout: 5
  })
  ElMessage.success('已在上一分支结束块后插入独立 else if 块：请在中间编排子步骤')
}

function addElseStep() {
  const endIdx = findNearestChainEndIndex()
  if (endIdx < 0) {
    ElMessage.warning('请先添加 if / else if 块，再添加 else')
    return
  }
  // 若链尾已是 else，禁止再加
  const start = (() => {
    for (let i = endIdx - 1; i >= 0; i--) {
      if (isFlowBlockStart(steps.value[i]) && findBlockEndIndex(steps.value, i) === endIdx) {
        return steps.value[i]
      }
    }
    return null
  })()
  if (start?.type === 'else') {
    ElMessage.warning('else 已是链路末尾，不能再追加')
    return
  }
  insertIndependentBlockAfter(endIdx, 'else', {})
  ElMessage.success('已在上一分支结束块后插入独立 else 块：请在中间编排子步骤')
}

function addLoopStep() {
  const depths = computeStepDepths(steps.value)
  const base = depths.length ? depths[depths.length - 1] : 0
  quickAddStep('loop', {
    loop_mode: 'while',
    loop_count: 10,
    condition: '条件成立',
    loop_body: '循环体步骤',
    indent: base
  })
  quickAddStep('end_block', { block_type: 'loop', remark: '', indent: base })
  nextTick(() => collapseAllBlocks())
  ElMessage.info('已插入 while 块：请点「while循环」行的「此后插入」，或点「结束块」的「块内插入」，在中间编排子步骤')
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
  if (!taskId.value) {
    ElMessage.warning('请先保存用例后再添加批注')
    return
  }
  showCommentDialog.value = true
  newComment.value = ''
  try {
    const res = await commentApi.list('test_case', taskId.value)
    comments.value = res.data || []
  } catch {
    comments.value = []
  }
}

async function submitComment() {
  if (!newComment.value.trim() || !taskId.value) return
  commentSaving.value = true
  try {
    await commentApi.create({
      asset_type: 'test_case',
      asset_id: Number(taskId.value),
      content: newComment.value.trim()
    })
    newComment.value = ''
    const res = await commentApi.list('test_case', taskId.value)
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

function findBlockStartIndex(list, endIdx) {
  if (!list[endIdx] || list[endIdx].type !== 'end_block') return -1
  let depth = 1
  for (let i = endIdx - 1; i >= 0; i--) {
    const t = list[i].type
    if (t === 'end_block') depth += 1
    else if (isFlowBlockStartType(t)) {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return -1
}

/** 拖 if / else if / else / while 或其结束块时，整块（含嵌套步骤）一起移动 */
function getStepMoveRange(list, index) {
  const step = list[index]
  if (!step) return { start: index, end: index }
  if (isFlowBlockStart(step)) {
    const end = findBlockEndIndex(list, index)
    if (end >= index) return { start: index, end }
  }
  if (step.type === 'end_block') {
    const start = findBlockStartIndex(list, index)
    if (start >= 0) return { start, end: index }
  }
  return { start: index, end: index }
}

function isStepDraggingIndex(index) {
  const r = dragStepRange.value
  return !!(r && index >= r.start && index <= r.end)
}

function onRemarkMouseDown(e) {
  // 描述框内选中/复制时，临时关闭整行拖拽，避免抢手势
  e.stopPropagation()
  const row = e.currentTarget?.closest?.('.step-item')
  if (!row) return
  row.setAttribute('draggable', 'false')
  const restore = () => {
    row.setAttribute('draggable', 'true')
    window.removeEventListener('mouseup', restore)
    window.removeEventListener('dragend', restore)
  }
  window.addEventListener('mouseup', restore)
  window.addEventListener('dragend', restore)
}

function onStepDragStart(e, index) {
  const t = e.target
  if (t?.closest?.('input, textarea, .el-input, .step-remark-row, .step-remark-inline, .step-meta-row, .step-remark-input, .el-checkbox, .el-switch, button, a')) {
    e.preventDefault()
    return
  }
  const range = getStepMoveRange(steps.value, index)
  dragFromIndex.value = range.start
  dragStepRange.value = range
  dragStepId.value = steps.value[range.start]?.id ?? null
  try { e.dataTransfer.setData('text/plain', String(range.start)) } catch { /* ignore */ }
  e.dataTransfer.effectAllowed = 'move'
}

function onStepDragOver(e, index) {
  e.dataTransfer.dropEffect = 'move'
}

function onStepDrop(e, toIndex) {
  const range = dragStepRange.value || getStepMoveRange(steps.value, dragFromIndex.value)
  const fromStart = range.start
  const fromEnd = range.end
  if (fromStart < 0 || toIndex < 0) return
  if (toIndex >= fromStart && toIndex <= fromEnd) return

  const arr = [...steps.value]
  const count = fromEnd - fromStart + 1
  const chunk = arr.splice(fromStart, count)
  let insertAt = toIndex
  if (toIndex > fromEnd) insertAt = toIndex - count
  arr.splice(insertAt, 0, ...chunk)
  steps.value = arr

  const edit = editingIndex.value
  if (edit != null) {
    if (edit >= fromStart && edit <= fromEnd) {
      editingIndex.value = insertAt + (edit - fromStart)
    } else {
      let next = edit
      if (edit > fromEnd) next -= count
      if (next >= insertAt) next += count
      editingIndex.value = next
    }
  }
}

function onStepDragEnd() {
  dragFromIndex.value = -1
  dragStepId.value = null
  dragStepRange.value = null
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
  if (!taskId.value) {
    ElMessage.warning('请先保存用例后再查看版本')
    return
  }
  showVersionDialog.value = true
  versionLoading.value = true
  try {
    const res = await caseApi.versions(taskId.value)
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
    await caseApi.rollback(taskId.value, ver.id)
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
  if (String(c.id) === String(taskId.value)) {
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

function parseCommonStepsContent(text) {
  try {
    const obj = JSON.parse(text || '{}')
    if (Array.isArray(obj)) return obj
    if (obj && Array.isArray(obj.steps)) return obj.steps
  } catch { /* ignore */ }
  return []
}

async function resolveCommonStepRow(name) {
  const n = String(name || '').trim()
  if (!n) return null
  let row = (commonSteps.value || []).find(s => s.name === n) || null
  if (!row) {
    try {
      commonSteps.value = (await commonStepApi.list()).data || []
      row = commonSteps.value.find(s => s.name === n) || null
    } catch { /* ignore */ }
  }
  if (row?.id) {
    try {
      const res = await commonStepApi.get(row.id)
      if (res.data) row = res.data
    } catch { /* keep list row */ }
  }
  return row
}

async function viewCommonStep(step) {
  const name = step?.common_step
  if (!name) {
    ElMessage.warning('未绑定公共步骤名称')
    return
  }
  const keepPrev = showCommonView.value && commonViewRow.value
  if (keepPrev) {
    commonViewStack.value.push({
      row: commonViewRow.value,
      steps: commonViewSteps.value
    })
  } else {
    commonViewStack.value = []
  }
  showCommonView.value = true
  commonViewLoading.value = true
  if (!keepPrev) {
    commonViewRow.value = null
    commonViewSteps.value = []
  }
  try {
    const row = await resolveCommonStepRow(name)
    if (!row) {
      if (keepPrev) commonViewStack.value.pop()
      else showCommonView.value = false
      ElMessage.warning(`未找到公共步骤「${name}」`)
      return
    }
    commonViewRow.value = row
    commonViewSteps.value = parseCommonStepsContent(row.steps_content)
  } finally {
    commonViewLoading.value = false
  }
}

function backCommonView() {
  const prev = commonViewStack.value.pop()
  if (!prev) return
  commonViewRow.value = prev.row
  commonViewSteps.value = prev.steps
}

function closeCommonView() {
  showCommonView.value = false
  commonViewStack.value = []
  commonViewRow.value = null
  commonViewSteps.value = []
}

function goEditViewedCommonStep() {
  const id = commonViewRow.value?.id
  if (!id) return
  closeCommonView()
  router.push({ path: `/common-steps/${id}/edit`, query: { returnTo: route.fullPath } })
}

async function saveCommonViewScript() {
  const row = commonViewRow.value
  if (!row?.id) {
    ElMessage.warning('当前公共步骤无法保存')
    return
  }
  // 规范化 script 字段
  for (const s of commonViewSteps.value || []) {
    if (s?.type === 'custom_script' && s.script_code == null && s.script != null) {
      s.script_code = s.script
    }
  }
  try {
    let payload = { version: 1, steps: commonViewSteps.value }
    try {
      const raw = JSON.parse(row.steps_content || '{}')
      if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
        payload = { ...raw, steps: commonViewSteps.value }
      }
    } catch { /* use default envelope */ }
    await commonStepApi.update(row.id, {
      name: row.name,
      description: row.description,
      steps_content: JSON.stringify(payload)
    })
    row.steps_content = JSON.stringify(payload)
    ElMessage.success('脚本已保存到公共步骤')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  }
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
    if (catalogId) {
      stepAddPanelRef.value?.selectCatalog?.(catalogId, { keepCurrent: false, openDialog: true })
    } else {
      stepAddPanelRef.value?.startAddStep?.()
    }
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
    remark: ''
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
    if (!taskId.value) payload.case_status = 'draft'
    if (taskId.value) {
      await caseApi.update(taskId.value, payload)
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
    let caseId = taskId.value
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
      if (taskId.value) {
        await caseApi.update(taskId.value, buildCasePayload())
        ElMessage.success(mode === 'active' ? '已提交生效' : mode === 'draft' ? '草稿已保存' : '用例已更新')
      } else {
        const res = await caseApi.create(buildCasePayload())
        ElMessage.success(mode === 'active' ? '已创建并提交生效' : '用例已创建')
        router.replace(`/cases/editor/${res.data.id}?asset=1`)
      }
      lastSavedSnapshot = takeEditorSnapshot()
      dirty = false
      autoSaveHint.value = `已保存 ${new Date().toLocaleTimeString()}`
      markJustSaved()
    } else if (taskId.value) {
      await taskApi.update(taskId.value, buildPayload())
      ElMessage.success('任务已更新')
      markJustSaved()
    } else {
      const res = await taskApi.create(buildPayload())
      ElMessage.success('任务已创建')
      markJustSaved()
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
  let id = taskId.value
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
  if (!taskId.value) {
    lastSavedSnapshot = takeEditorSnapshot()
    return
  }
  if (isAssetMode) {
    const res = await caseApi.get(taskId.value)
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
      steps.value = (parsed.steps || []).map(mapLoadedStep)
      nextTick(() => collapseAllBlocks())
    } catch { steps.value = [] }
    lastSavedSnapshot = takeEditorSnapshot()
    dirty = false
    return
  }
  const res = await taskApi.get(taskId.value)
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
    steps.value = (parsed.steps || []).map(mapLoadedStep)
    nextTick(() => collapseAllBlocks())
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
  // 路由名合并后组件不重挂载：仅在切换到「另一条已有用例」时重新拉取
  watch(
    () => route.params.id,
    async (id, prev) => {
      if (!prev && id) return // 新建自动保存后补 id，保持当前编辑态
      if (String(id || '') === String(prev || '')) return
      await loadTask()
      consumePickFromElementPicker()
      consumeInvokeCommonQuery()
    }
  )
})

onBeforeUnmount(() => {
  stopAutoSave()
  if (justSavedTimer) clearTimeout(justSavedTimer)
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
.vce-page {
  max-width: none;
  padding-top: 16px;
}

.vce-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 14px 18px;
  background: var(--atp-bg-elevated, #fff);
  border: 1px solid var(--atp-border-neutral, #e8edf3);
  border-radius: 12px;
}
.vce-topbar-left {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.vce-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--atp-text, #0f172a);
  line-height: 1.3;
}
.flow-progress {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 2px;
  align-items: center;
}
.flow-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  padding: 4px 8px;
  border-radius: 999px;
  cursor: pointer;
  color: #94a3b8;
  transition: background 0.15s, color 0.15s;
}
.flow-step:hover { background: #f1f5f9; color: #64748b; }
.flow-step.done { color: var(--atp-accent, #6366f1); }
.flow-step.current,
.flow-step.active {
  background: rgba(99, 102, 241, 0.1);
  color: var(--atp-accent, #6366f1);
  font-weight: 600;
}
.flow-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  background: #e2e8f0;
  color: #64748b;
}
.flow-step.done .flow-dot,
.flow-step.current .flow-dot {
  background: var(--atp-accent, #6366f1);
  color: #fff;
}
.flow-label { font-size: 12px; white-space: nowrap; }
.flow-step:not(:last-child)::after {
  content: '';
  width: 12px;
  height: 1px;
  background: #e2e8f0;
  margin-left: 2px;
}

.vce-topbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}
.btn-more {
  --el-button-bg-color: #f8fafc;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}
.more-caret { font-size: 10px; margin-left: 2px; }

.main-row {
  margin-bottom: 16px;
  align-items: stretch;
}
.main-row > .el-col {
  display: flex;
  flex-direction: column;
}
.meta-card,
.workspace-card {
  flex: 1;
  width: 100%;
  display: flex !important;
  flex-direction: column;
  height: 100%;
  margin-bottom: 0 !important;
  border-radius: 12px !important;
  border: 1px solid var(--atp-border-neutral, #e8edf3) !important;
  box-shadow: none !important;
}
.meta-card :deep(.el-card__body),
.workspace-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
  padding: 14px 16px 16px;
}

.meta-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
.meta-form :deep(.el-form-item__label) {
  font-size: 12px;
  color: #64748b;
  padding-bottom: 4px !important;
  line-height: 1.3;
}
.meta-form :deep(.el-input__wrapper),
.meta-form :deep(.el-select .el-select__wrapper) {
  min-height: 32px;
}
.meta-section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--atp-text);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eef2f7;
}
.req-hint {
  font-size: 11px;
  font-weight: 400;
  color: #f87171;
  margin-left: 6px;
}
.req-label::after {
  content: ' *';
  color: #ef4444;
}
.is-required-strong :deep(.el-form-item__label)::before { display: none; }
.folder-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.folder-select { flex: 1; min-width: 0; }
.priority-group :deep(.el-radio-button__inner) {
  padding: 6px 12px;
  border-radius: 6px !important;
  box-shadow: none !important;
  border: 1px solid #e2e8f0 !important;
  margin-right: 4px;
  background: #fff;
}
.priority-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--atp-accent, #6366f1) !important;
  border-color: var(--atp-accent, #6366f1) !important;
  color: #fff !important;
  box-shadow: none !important;
}
.compact-textarea :deep(textarea) {
  min-height: 32px !important;
  resize: vertical;
}
.compact-textarea :deep(.el-textarea__inner::placeholder) {
  color: #cbd5e1;
}

.steps-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.steps-toolbar-panel {
  flex-shrink: 0;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef2f7;
}
.toolbar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.toolbar-row--search { margin-bottom: 10px; }
.step-search { width: 280px; max-width: 100%; }
.steps-count { font-size: 12px; color: var(--atp-text-secondary); }
.steps-drag-hint {
  font-size: 12px;
  color: #94a3b8;
  margin-left: auto;
}
.toolbar-groups { gap: 12px 16px; }
.tb-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding-right: 12px;
  border-right: 1px solid #eef2f7;
}
.tb-group:last-child { border-right: none; padding-right: 0; }
.tb-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
}

.steps-scroll {
  flex: 1;
  min-height: 0;
  /* 含描述行后约 10 步可视，超出滚动 */
  max-height: calc(10 * 92px);
  overflow-y: auto;
  padding-right: 4px;
}
.steps-scroll:has(.steps-empty) {
  min-height: 360px;
  max-height: none;
}

.steps-empty {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 32px 16px;
}
.empty-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: var(--atp-text);
}
.empty-sub {
  margin: 0 0 20px;
  font-size: 13px;
  color: #94a3b8;
}
.empty-entries {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 220px));
  gap: 12px;
  width: 100%;
  max-width: 720px;
  justify-content: center;
}
.empty-entry {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  text-align: left;
  padding: 14px 14px 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.empty-entry:hover {
  border-color: #c7d2fe;
  box-shadow: 0 6px 18px rgba(99, 102, 241, 0.08);
  transform: translateY(-1px);
}
.empty-entry--primary {
  border-color: #a5b4fc;
  background: linear-gradient(180deg, #eef2ff 0%, #fff 70%);
}
.empty-entry strong {
  font-size: 13px;
  color: #0f172a;
}
.empty-entry > span:last-child {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.45;
}
.entry-badge {
  font-size: 11px;
  font-weight: 700;
  color: var(--atp-accent, #6366f1);
}
.empty-advanced {
  margin: 18px 0 0;
  font-size: 12px;
  color: #cbd5e1;
}

.step-block {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  margin-bottom: 14px;
  width: 100%;
}
.step-meta-row {
  display: flex;
  align-items: center;
  gap: 0;
  min-width: 0;
  padding: 0 0 6px;
  border-bottom: 1px solid #f0f0f0;
}
.step-index-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #86909c;
  line-height: 22px;
  font-weight: 400;
  white-space: nowrap;
}
.step-remark-input {
  flex: 1;
  min-width: 0;
  max-width: none;
  width: auto;
}
.step-remark-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 4px !important;
}
.step-remark-input :deep(.el-input__inner) {
  font-size: 13px;
  color: #86909c;
  line-height: 22px;
  height: 22px;
}
.step-remark-input :deep(.el-input__inner::placeholder) {
  color: #c9cdd4;
}
.step-remark-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: none !important;
}
.step-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  margin-bottom: 0;
  border-radius: 10px;
  border: 1px solid transparent;
  transition: box-shadow 0.15s, transform 0.15s;
  position: relative;
  box-sizing: border-box;
  width: 100%;
}
.step-item:hover { box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06); }
.step-item.is-dragging { opacity: 0.55; }
.step-item.disabled { opacity: 0.55; }
.step-item.selected { outline: 1px solid var(--el-color-primary); }
.step-item.is-nested {
  border-left: 3px solid #c7d2fe;
}
.step-item.is-block {
  border-left: 3px solid var(--atp-accent, #6366f1);
}
.block-toggle {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  margin-top: 4px;
  border: 1px solid #c7d2fe;
  border-radius: 6px;
  background: #eef2ff;
  color: var(--atp-accent, #6366f1);
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
  padding: 0;
}
.block-toggle:hover {
  background: #e0e7ff;
  border-color: #a5b4fc;
}
.block-collapsed-tag {
  flex-shrink: 0;
}
.drag-handle {
  cursor: grab;
  color: #94a3b8;
  font-size: 14px;
  line-height: 1;
  user-select: none;
  padding: 8px 6px 0;
  flex-shrink: 0;
}
.drag-handle:active { cursor: grabbing; }
.step-item.tone-wait { background: #f8fafc; border-color: #e2e8f0; }
.step-item.tone-click { background: #eff6ff; border-color: #bfdbfe; }
.step-item.tone-assert { background: #ecfdf5; border-color: #a7f3d0; }
.step-item.tone-app { background: #fff7ed; border-color: #fed7aa; }
.step-item.tone-default { background: #f8fafc; border-color: #e2e8f0; }

.comment-list { max-height: 320px; overflow-y: auto; }
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
  background: linear-gradient(135deg, var(--atp-accent, #6366f1), var(--atp-primary, #0284c7));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
  margin-top: 2px;
}
.jlc-step-top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px 0;
  min-width: 0;
}
.jlc-step-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #86909c;
  line-height: 22px;
  font-weight: 500;
}
.step-enable-switch {
  margin-left: auto;
  flex-shrink: 0;
}
.step-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  min-width: 0;
  padding-top: 2px;
}
.step-main-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}
.step-remark-row {
  display: flex;
  align-items: center;
  gap: 8px;
  /* 固定等宽，左对齐 */
  width: 260px;
  max-width: 100%;
  margin-right: auto;
  flex-shrink: 0;
  user-select: text;
  -webkit-user-select: text;
}
.step-remark-label {
  flex-shrink: 0;
  font-size: 12px;
  color: #94a3b8;
  width: 28px;
}
.step-remark-row :deep(.el-input) {
  flex: 1;
  min-width: 0;
}
.step-remark-row :deep(.el-input__wrapper),
.step-remark-row :deep(.el-input__inner) {
  background: rgba(255, 255, 255, 0.72);
  user-select: text;
  -webkit-user-select: text;
  cursor: text;
}
.step-item :deep(.el-checkbox) {
  margin-top: 6px;
}
.step-desc {
  color: var(--atp-text-secondary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.invoke-common-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  padding: 10px 12px;
  background: #fff !important;
  border: 1px solid #e5e6eb !important;
  border-radius: 4px;
  box-shadow: none;
  position: relative;
}
.invoke-common-card .icc-top {
  position: absolute;
  top: 6px;
  right: 8px;
  z-index: 2;
}
.invoke-common-card:hover {
  box-shadow: none;
  border-color: #c9cdd4 !important;
}
.icc-top {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 0;
}
.icc-top:empty {
  display: none;
}
.icc-step-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #86909c;
  line-height: 22px;
  font-weight: 500;
}
.icc-disabled-hint {
  font-size: 12px;
  color: #86909c;
}
.icc-top-actions {
  margin-left: auto;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0;
  opacity: 0;
  transition: opacity 0.15s;
}
.invoke-common-card:hover .icc-top-actions,
.invoke-common-card.selected .icc-top-actions {
  opacity: 1;
}
.icc-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  width: 100%;
}
.common-step-use-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 2px;
  font-size: 12px;
  line-height: 18px;
  color: #ff7d00;
  background: #fff;
  border: 1px solid #ff9a2e;
}
.common-step-name-box {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  border-radius: 2px;
  font-size: 13px;
  line-height: 20px;
  color: #4e5969;
  background: #f2f3f5;
  border: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.icc-view-btn {
  flex-shrink: 0;
  color: #1d2129 !important;
  background: #fff !important;
  border: 1px solid #c9cdd4 !important;
  border-radius: 2px;
  padding: 7px 14px;
  height: auto;
  font-size: 13px;
}
.icc-view-btn:hover {
  border-color: #86909c !important;
  color: #1d2129 !important;
  background: #f7f8fa !important;
}

/* 主列表：判断 / 循环卡片 */
.judge-list-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0;
  padding: 0 !important;
  background: #fff !important;
  border: 1px solid #e5e6eb !important;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: none;
}
.judge-list-card:hover {
  box-shadow: none;
  border-color: #c9cdd4 !important;
}
.jlc-loop-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fffbf5;
  border-bottom: 1px solid #f5e6d3;
  cursor: pointer;
  user-select: none;
}
.jlc-loop-tag {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #e8a054;
  background: #fff;
  border: 1px solid #f0c48a;
}
.jlc-loop-hint {
  flex: 1;
  font-size: 13px;
  color: #165dff;
}
.jlc-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  user-select: none;
  min-width: 0;
}
.jlc-check {
  flex-shrink: 0;
  transform: scale(0.9);
}
.jlc-drag {
  flex-shrink: 0;
  cursor: grab;
  color: #c9cdd4;
  padding: 0 2px;
  user-select: none;
}
.jlc-drag:active {
  cursor: grabbing;
}
.jlc-if-icon {
  flex-shrink: 0;
  min-width: 28px;
  height: 22px;
  padding: 0 6px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #d9892b;
  background: #fff3e0;
  border: 1px solid #f0c48a;
}
.jlc-mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}
.jlc-title {
  flex-shrink: 0;
  max-width: 36%;
  font-size: 14px;
  font-weight: 500;
  color: #165dff;
  line-height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.jlc-action-pill {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  max-width: 260px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #165dff;
  background: #e8f3ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.jlc-assert-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #4e5969;
}
.jlc-pill {
  flex-shrink: 1;
  min-width: 0;
  max-width: 220px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #1d2129;
  background: #f2f3f5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.jlc-pill-warn {
  color: #c9a227;
  background: #fff8ee;
  border: 1px dashed #f0c48a;
}
.jlc-assert-state {
  flex-shrink: 0;
  font-size: 13px;
  color: #1d2129;
}
.jlc-status {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #d9892b;
  background: #fff8ee;
  border: 1px solid #f0c48a;
}
.jlc-chevron {
  flex-shrink: 0;
  color: #86909c;
  font-size: 14px;
  transition: transform 0.15s;
}
.jlc-chevron.open {
  transform: rotate(180deg);
}
.jlc-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0;
  padding: 0 10px 8px;
  opacity: 0;
  transition: opacity 0.15s;
}
.judge-list-card:hover .jlc-actions,
.judge-list-card.selected .jlc-actions {
  opacity: 1;
}
.jlc-body {
  padding: 4px 14px 12px 16px;
  border-top: 1px solid #f2f3f5;
  background: #fcfcfd;
}
.jlc-nested-judge {
  margin: 0;
}
.jlc-child {
  display: grid;
  grid-template-columns: 16px 1fr;
  gap: 8px;
  margin-top: 0;
  position: relative;
  padding: 8px 0;
}
.jlc-child:not(:last-child) .jlc-child-rail::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 14px;
  bottom: -8px;
  width: 2px;
  margin-left: -1px;
  background: #e5e6eb;
  z-index: 0;
  pointer-events: none;
}
.jlc-child-rail {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 4px;
  z-index: 1;
}
.jlc-child-content {
  min-width: 0;
}
.jlc-child-step {
  padding: 0;
}
.jlc-child-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #86909c;
  position: relative;
  z-index: 1;
}
.jlc-child-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f98981;
  box-shadow: 0 0 0 2px rgba(249, 137, 129, 0.25);
  flex-shrink: 0;
  z-index: 2;
}
.jlc-child-dot.dot-block {
  background: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}
.jlc-child-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-left: 0;
}
.jlc-child-action {
  font-size: 13px;
  color: #165dff;
  line-height: 22px;
}
.jlc-child-text {
  font-size: 13px;
  color: #1d2129;
  line-height: 22px;
}
.jlc-empty {
  padding: 10px 0 4px;
  font-size: 12px;
  color: #c9cdd4;
}

.step-locator {
  color: var(--el-color-info);
  font-size: 12px;
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
.right-tabs :deep(.el-tabs__item.is-active) {
  color: var(--atp-accent, #6366f1);
  font-weight: 600;
}
.right-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--atp-accent, #6366f1);
}
.right-tabs :deep(.el-tabs__item:hover) {
  color: var(--atp-accent, #6366f1);
}
.right-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.right-tabs :deep(.el-tab-pane) { height: 100%; }

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
.vce-fab-record:hover { filter: brightness(1.05); }

.pool-picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.pool-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.pool-empty {
  margin: 12px 0 0;
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
}
.pool-radio {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid #cbd5e1;
  border-radius: 50%;
  vertical-align: middle;
}
.pool-radio.checked {
  border-color: var(--atp-primary, #8B6CF0);
  background: #fff;
  box-shadow: inset 0 0 0 3px var(--atp-primary, #8B6CF0);
}
:deep(.el-table .is-pool-selected > td) {
  background: rgba(99, 102, 241, 0.08) !important;
}
.pool-pick-count {
  font-size: 13px;
  color: var(--atp-text-secondary, #64748b);
}
.step-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  flex-shrink: 0;
  width: 292px;
  max-width: 100%;
  padding-top: 2px;
}
.step-actions :deep(.el-button) {
  margin: 0;
  width: 100%;
  padding-left: 4px;
  padding-right: 4px;
}
.btn-copy {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}

.form-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.common-view-desc {
  margin: 0 0 12px;
  color: var(--atp-text-secondary, #64748b);
  font-size: 13px;
  line-height: 1.5;
}
.common-view-body {
  min-height: 120px;
}
.script-code-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 1200px) {
  .empty-entries { grid-template-columns: 1fr; max-width: 360px; }
  .steps-drag-hint { margin-left: 0; }
  .tb-group { border-right: none; padding-right: 0; }
}
@media (max-width: 992px) {
  .vce-topbar-actions { width: 100%; justify-content: flex-start; }
  .flow-label { display: none; }
}
</style>

