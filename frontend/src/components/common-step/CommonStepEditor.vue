<template>
  <div class="cse">
    <div class="cse-toolbar">
      <el-radio-group v-model="uiMode" size="small" @change="onUiModeChange">
        <el-radio-button value="simple">简易模式</el-radio-button>
        <el-radio-button value="pro">专业模式</el-radio-button>
      </el-radio-group>
      <div class="cse-toolbar-right">
        <el-button v-if="uiMode === 'simple'" size="small" @click="showTemplates = true">套用模板</el-button>
        <el-button size="small" @click="goPicker">控件拾取</el-button>
      </div>
    </div>

    <!-- 简易：三步 -->
    <div v-if="uiMode === 'simple'" class="cse-simple">
      <el-steps :active="wizardStep" finish-status="success" align-center class="cse-steps">
        <el-step title="基础信息" description="名称 / 类型 / 适用端" />
        <el-step title="执行配置" description="可连续添加多条步骤" />
        <el-step title="高级与保存" description="预览后入库" />
      </el-steps>

      <section v-show="wizardStep === 0" class="cse-card" data-guide="basic">
        <div class="cse-card-title">基础信息 <span class="hint">仅 3 项必填</span></div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="步骤名称" required :error="errors.name">
            <el-input
              v-model="simple.name"
              maxlength="80"
              show-word-limit
              placeholder="例：关闭广告弹窗、机器人回充"
              @input="onNameInput"
            />
            <div v-if="nameSuggestions.length" class="suggest-row">
              <span class="suggest-label">推荐模板：</span>
              <el-tag
                v-for="t in nameSuggestions"
                :key="t.id"
                size="small"
                class="suggest-tag"
                @click="applyTpl(t.id)"
              >{{ t.name }}</el-tag>
            </div>
          </el-form-item>
          <el-form-item label="步骤类型（大类）" required>
            <el-select v-model="simple.category" style="width:100%" @change="onCategoryChange">
              <el-option
                v-for="c in CATEGORIES"
                :key="c.key"
                :label="c.label"
                :value="c.key"
              >
                <span>{{ c.label }}</span>
                <span class="opt-hint">{{ c.hint }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="适用端" required>
            <el-radio-group v-model="simple.platform">
              <el-radio-button v-for="p in PLATFORMS" :key="p.key" :value="p.key">{{ p.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </section>

      <section v-show="wizardStep === 1" class="cse-card" data-guide="action">
        <div class="cse-card-title">
          执行配置
          <span class="hint">可多次「添加本条」，一条公共步骤内编排多步</span>
        </div>

        <div class="draft-steps-panel">
          <div class="draft-steps-head">
            <strong>已添加步骤（{{ visibleDraftItems.length }} / {{ draftSteps.length }}）</strong>
            <span v-if="draftSteps.length" class="draft-drag-hint">拖拽整行可调序；拖 if/else/while 会整块移动</span>
            <div class="draft-steps-head-actions">
              <el-button
                v-if="hasDraftBlocks"
                link
                size="small"
                @click="expandAllDraftBlocks"
              >全部展开</el-button>
              <el-button
                v-if="hasDraftBlocks"
                link
                size="small"
                @click="collapseAllDraftBlocks"
              >全部收起</el-button>
              <el-button
                v-if="draftSteps.length"
                link
                type="danger"
                size="small"
                @click="clearDraftSteps"
              >清空列表</el-button>
            </div>
          </div>
          <div v-if="!draftSteps.length" class="draft-steps-empty">尚未添加子步骤，配置下方动作后点击「添加本条到列表」</div>
          <div v-else class="draft-steps-list">
            <div
              v-for="item in visibleDraftItems"
              :key="item.step._uid || item.index"
              class="draft-step-row"
              :class="{
                editing: editingDraftIndex === item.index,
                'is-block': isDraftFlowHeader(item.step),
                'is-nested': item.depth > 0,
                'is-end': item.step.type === 'end_block',
                'is-dragging': isDraftDraggingIndex(item.index)
              }"
              :style="{ marginLeft: `${item.depth * 16}px` }"
              draggable="true"
              @dragstart="onDraftDragStart($event, item.index)"
              @dragover.prevent="onDraftDragOver"
              @drop.prevent="onDraftDrop($event, item.index)"
              @dragend="onDraftDragEnd"
            >
              <span class="draft-drag-handle" title="拖拽排序">⋮⋮</span>
              <button
                v-if="isDraftFlowHeader(item.step)"
                type="button"
                class="draft-block-toggle"
                :title="collapsedDraftIds.has(item.step._uid) ? '展开块内步骤' : '收起块内步骤'"
                @click.stop="toggleDraftBlock(item.step._uid)"
              >{{ collapsedDraftIds.has(item.step._uid) ? '▶' : '▼' }}</button>
              <span v-else class="draft-block-spacer" />
              <span class="draft-step-idx">{{ item.index + 1 }}</span>
              <el-tag
                size="small"
                effect="plain"
                class="draft-type-tag"
                :class="draftStepToneClass(item.step)"
              >{{ draftStepTypeLabel(item.step) }}</el-tag>
              <span class="draft-step-desc">{{ draftStepLabel(item.step) }}</span>
              <el-tag
                v-if="collapsedDraftIds.has(item.step._uid) && item.collapsedCount > 0"
                size="small"
                type="info"
                effect="plain"
              >已收起 {{ item.collapsedCount }} 步</el-tag>
              <div class="draft-step-actions">
                <el-button text size="small" type="primary" @click.stop="editDraftStep(item.index)">改</el-button>
                <el-button text size="small" type="danger" @click.stop="removeDraftStep(item.index)">删</el-button>
              </div>
            </div>
          </div>
        </div>

        <el-form label-position="top" @submit.prevent>
          <el-form-item label="具体动作" required>
            <el-select v-model="simple.action_key" style="width:100%" filterable>
              <el-option
                v-for="a in currentActions"
                :key="a.key"
                :label="a.label + (a.hazardous ? '（高危）' : '')"
                :value="a.key"
              />
            </el-select>
          </el-form-item>

          <template v-if="fieldHas('locator') && (!fieldHas('condition_kind') || conditionNeedsLocator(simple.condition_kind))">
            <el-form-item label="定位方式">
              <div class="locator-row">
                <el-select v-model="simple.locator_type" style="width:120px">
                  <el-option label="id" value="id" />
                  <el-option label="text" value="text" />
                  <el-option label="xpath" value="xpath" />
                  <el-option label="desc" value="content_desc" />
                  <el-option label="坐标" value="bounds" />
                </el-select>
                <el-input v-model="simple.locator_value" placeholder="定位值，可从控件拾取/控件库回填" />
                <el-button type="primary" plain @click="goPicker">拾取</el-button>
                <el-button plain @click="openPoolPicker('locator')">控件库</el-button>
              </div>
            </el-form-item>
            <el-form-item label="控件名称">
              <el-input v-model="simple.element_name" placeholder="可选，便于步骤可读" />
            </el-form-item>
          </template>

          <el-form-item v-if="fieldHas('condition_kind')" label="判断类型" required>
            <el-select v-model="simple.condition_kind" style="width:100%" @change="onConditionKindChange">
              <el-option
                v-for="c in CONDITION_KINDS"
                :key="c.value"
                :label="c.label"
                :value="c.value"
              />
            </el-select>
            <p class="defaults-hint" style="margin-top:6px">
              控件类条件需配置定位；「变量等于」填写参数键与期望机型值
            </p>
          </el-form-item>
          <el-form-item
            v-if="fieldHas('condition_kind') && conditionNeedsVarName(simple.condition_kind)"
            label="变量名"
            required
          >
            <el-input v-model="simple.var_name" placeholder="例：product_id 或 {{product_id}}" />
          </el-form-item>
          <el-form-item v-if="fieldHas('condition') && simple.condition_kind === 'custom'" label="自定义条件" required>
            <el-input v-model="simple.condition" placeholder="例：屏幕出现某文案，或 true/false" />
          </el-form-item>
          <el-form-item v-if="fieldHas('branch_true')" label="成立分支说明">
            <el-input v-model="simple.branch_true" placeholder="可选备注，真正步骤请插在 if 与 else/结束块之间" />
          </el-form-item>
          <el-form-item v-if="fieldHas('branch_false')" label="否则说明（备注）">
            <el-input v-model="simple.branch_false" placeholder="可选备注；不成立逻辑请添加 else if / else 步骤" />
          </el-form-item>
          <el-form-item v-if="fieldHas('block_type')" label="结束块类型">
            <el-select v-model="simple.block_type" style="width:100%">
              <el-option label="结束分支" value="branch" />
              <el-option label="结束循环" value="loop" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fieldHas('text')" label="输入文本">
            <el-input v-model="simple.text" placeholder="支持 ${变量}" />
          </el-form-item>
          <el-form-item v-if="fieldHas('seconds')" label="等待秒数">
            <el-input-number v-model="simple.seconds" :min="0" :max="120" :step="0.5" />
          </el-form-item>
          <el-form-item v-if="fieldHas('timeout')" label="超时（秒）">
            <el-input-number v-model="simple.timeout" :min="1" :max="120" />
          </el-form-item>
          <el-form-item v-if="fieldHas('duration_ms')" label="长按时长(ms)">
            <el-input-number v-model="simple.duration_ms" :min="200" :max="5000" :step="100" />
          </el-form-item>
          <template v-if="fieldHas('swipe_coords')">
            <el-form-item label="滑动坐标">
              <div class="coords-block">
                <div class="coords-toolbar">
                  <el-button type="primary" size="small" @click="openPoolPicker('swipe_start')">从控件库选起点</el-button>
                  <el-button type="primary" size="small" @click="openPoolPicker('swipe_end')">从控件库选终点</el-button>
                  <el-button plain size="small" @click="useManualCoords">手动填坐标</el-button>
                </div>
                <div class="swipe-pool-rows">
                  <div class="pool-control-card" :class="{ filled: !!simple.swipe_start_name }">
                    <div class="pool-control-main">
                      <span class="pool-control-tag">起点</span>
                      <strong v-if="simple.swipe_start_name" class="pool-control-name">{{ simple.swipe_start_name }}</strong>
                      <span v-else class="pool-control-placeholder">尚未选择起点坐标控件</span>
                    </div>
                    <div class="pool-control-actions">
                      <el-button type="primary" plain size="small" @click="openPoolPicker('swipe_start')">
                        {{ simple.swipe_start_name ? '更换' : '选择' }}
                      </el-button>
                      <el-button
                        v-if="simple.swipe_start_name"
                        type="danger"
                        plain
                        size="small"
                        @click="clearSwipePoint('start')"
                      >清除</el-button>
                    </div>
                  </div>
                  <div class="swipe-pool-arrow">↓ 滑动到</div>
                  <div class="pool-control-card" :class="{ filled: !!simple.swipe_end_name }">
                    <div class="pool-control-main">
                      <span class="pool-control-tag">终点</span>
                      <strong v-if="simple.swipe_end_name" class="pool-control-name">{{ simple.swipe_end_name }}</strong>
                      <span v-else class="pool-control-placeholder">尚未选择终点坐标控件</span>
                    </div>
                    <div class="pool-control-actions">
                      <el-button type="primary" plain size="small" @click="openPoolPicker('swipe_end')">
                        {{ simple.swipe_end_name ? '更换' : '选择' }}
                      </el-button>
                      <el-button
                        v-if="simple.swipe_end_name"
                        type="danger"
                        plain
                        size="small"
                        @click="clearSwipePoint('end')"
                      >清除</el-button>
                    </div>
                  </div>
                </div>
                <template v-if="simple.coords_mode !== 'pool'">
                  <div class="coords labeled" style="margin-top:10px">
                    <label>起点 X</label><el-input-number v-model="simple.x1" :min="0" />
                    <label>起点 Y</label><el-input-number v-model="simple.y1" :min="0" />
                    <span class="coords-arrow">→</span>
                    <label>终点 X</label><el-input-number v-model="simple.x2" :min="0" />
                    <label>终点 Y</label><el-input-number v-model="simple.y2" :min="0" />
                  </div>
                </template>
                <p class="defaults-hint">请分别从控件库选择起点、终点；或点「手动填坐标」输入数字</p>
              </div>
            </el-form-item>
          </template>
          <template v-if="fieldHas('xy')">
            <el-form-item label="点击坐标">
              <div class="coords-block">
                <div class="coords-toolbar">
                  <el-button type="primary" size="small" @click="openPoolPicker('tap')">从控件库选择</el-button>
                  <el-button plain size="small" @click="useManualCoords">手动填坐标</el-button>
                </div>
                <div v-if="simple.coords_mode === 'pool' && simple.element_name" class="pool-control-card filled">
                  <div class="pool-control-main">
                    <span class="pool-control-tag">坐标控件</span>
                    <strong class="pool-control-name">{{ simple.element_name }}</strong>
                  </div>
                  <div class="pool-control-actions">
                    <el-button type="primary" plain size="small" @click="openPoolPicker('tap')">更换</el-button>
                    <el-button type="danger" plain size="small" @click="clearPoolCoords">清除</el-button>
                  </div>
                </div>
                <template v-else>
                  <div class="coords labeled">
                    <label>X</label><el-input-number v-model="simple.x" :min="0" />
                    <label>Y</label><el-input-number v-model="simple.y" :min="0" />
                  </div>
                  <p class="defaults-hint">可点「从控件库选择」直接引用坐标控件名称</p>
                </template>
              </div>
            </el-form-item>
          </template>
          <template v-if="fieldHas('xy2')">
            <el-form-item label="拖拽终点">
              <div class="coords">
                <span>X2</span><el-input-number v-model="simple.x2" :min="0" />
                <span>Y2</span><el-input-number v-model="simple.y2" :min="0" />
              </div>
            </el-form-item>
          </template>
          <el-form-item v-if="fieldHas('key')" label="系统按键">
            <el-select v-model="simple.key" style="width:100%">
              <el-option label="返回" value="back" />
              <el-option label="主页" value="home" />
              <el-option label="多任务" value="recent" />
              <el-option label="电源" value="power" />
              <el-option label="音量+" value="volume_up" />
              <el-option label="音量-" value="volume_down" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fieldHas('common_step')" label="引用公共步骤" required>
            <el-select
              v-model="simple.common_step"
              filterable
              clearable
              style="width:100%"
              placeholder="选择要嵌套调用的公共步骤"
            >
              <el-option
                v-for="n in invokableCommonNames"
                :key="n"
                :label="n"
                :value="n"
              />
            </el-select>
            <p v-if="!invokableCommonNames.length" class="bind-hint">暂无可引用的公共步骤，请先保存其他公共步骤</p>
          </el-form-item>
          <el-form-item v-if="fieldHas('input_params_json')" label="入参 JSON">
            <el-input
              v-model="simple.input_params_json"
              type="textarea"
              :rows="2"
              placeholder='可选，例：{"username":"test1"}'
            />
          </el-form-item>
          <el-form-item v-if="fieldHas('loop_count')" label="循环次数">
            <el-input-number v-model="simple.loop_count" :min="1" :max="999" />
          </el-form-item>
          <el-form-item v-if="fieldHas('loop_body')" label="循环体说明">
            <el-input v-model="simple.loop_body" />
          </el-form-item>
          <el-form-item v-if="fieldHas('try_body')" label="try 主流程">
            <el-input v-model="simple.try_body" />
          </el-form-item>
          <el-form-item v-if="fieldHas('catch_body')" label="catch 兜底">
            <el-input v-model="simple.catch_body" />
          </el-form-item>
          <el-form-item v-if="fieldHas('robot_command')" label="机器人指令">
            <el-select v-model="simple.robot_command" style="width:100%" filterable>
              <el-option v-for="c in ROBOT_COMMANDS" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fieldHas('wait_after')" label="执行后等待(秒)">
            <el-input-number v-model="simple.wait_after" :min="0" :max="60" />
          </el-form-item>
          <el-form-item v-if="fieldHas('app_package')" label="应用包名">
            <el-input v-model="simple.app_package" placeholder="com.example.app" />
          </el-form-item>
          <el-form-item v-if="fieldHas('step_remark')" label="截图名称" required>
            <el-input
              v-model="simple.step_remark"
              placeholder="例：蓝牙列表页 / 登录成功页"
              maxlength="80"
              show-word-limit
            />
          </el-form-item>
          <el-form-item v-if="fieldHas('save_path')" label="截图保存路径">
            <el-input v-model="simple.save_path" placeholder="可选，默认任务目录" />
          </el-form-item>
          <el-form-item v-if="fieldHas('shell_cmd')" label="Shell 命令">
            <el-input v-model="simple.shell_cmd" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item
            v-if="fieldHas('expected') && (!fieldHas('condition_kind') || conditionNeedsExpected(simple.condition_kind))"
            :label="conditionNeedsVarName(simple.condition_kind) ? '期望值' : '预期结果'"
          >
            <el-input
              v-model="simple.expected"
              :placeholder="conditionNeedsVarName(simple.condition_kind)
                ? '例：AX17'
                : (fieldHas('condition_kind') ? '文本包含时填写期望文案' : '')"
            />
          </el-form-item>
          <el-form-item v-if="fieldHas('expected_count')" label="期望个数">
            <el-input-number v-model="simple.expected_count" :min="0" :max="100" />
          </el-form-item>
          <el-form-item v-if="fieldHas('attr_name')" label="属性名">
            <el-input v-model="simple.attr_name" placeholder="text / content-desc / enabled" />
          </el-form-item>
          <el-form-item v-if="fieldHas('actual')" label="实际值/变量">
            <el-input v-model="simple.actual" />
          </el-form-item>
          <el-form-item v-if="fieldHas('compare_op')" label="比较方式">
            <el-select v-model="simple.compare_op" style="width:100%">
              <el-option label="相等" value="equals" />
              <el-option label="包含" value="contains" />
              <el-option label="不相等" value="not_equals" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fieldHas('var_name') && !fieldHas('condition_kind')" label="变量名">
            <el-input v-model="simple.var_name" />
          </el-form-item>
          <el-form-item v-if="fieldHas('var_value')" label="变量值">
            <el-input v-model="simple.var_value" />
          </el-form-item>
          <el-form-item v-if="fieldHas('file_path')" label="文件路径">
            <el-input v-model="simple.file_path" />
          </el-form-item>
          <el-form-item v-if="fieldHas('http_method')" label="HTTP 方法">
            <el-select v-model="simple.http_method" style="width:140px">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fieldHas('http_url')" label="URL">
            <el-input v-model="simple.http_url" />
          </el-form-item>
          <el-form-item v-if="fieldHas('http_body')" label="Body">
            <el-input v-model="simple.http_body" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item v-if="fieldHas('script')" label="脚本内容" :required="isHazardous">
            <el-input v-model="simple.script" type="textarea" :rows="8" class="mono" />
          </el-form-item>
          <el-alert
            v-if="isHazardous"
            type="warning"
            :closable="false"
            show-icon
            title="高危步骤：保存前需确认，将立刻入库可用"
            style="margin-bottom:12px"
          />
          <el-alert
            v-if="simple.action_key === 'branch_if'"
            type="info"
            :closable="false"
            show-icon
            title="if 为独立块（含结束块）。条件不成立时请在该结束块之后紧挨添加「else if / else」独立块"
            style="margin-bottom:12px"
          />
          <el-alert
            v-if="simple.action_key === 'branch_else_if' || simple.action_key === 'branch_else'"
            type="info"
            :closable="false"
            show-icon
            title="else if / else 也是独立块（自带结束块），须紧接在上一 if/else if 的结束块之后，中间不要插其他步骤"
            style="margin-bottom:12px"
          />
          <el-alert
            v-if="editingDraftIndex !== null"
            type="warning"
            :closable="false"
            show-icon
            :title="`正在修改第 ${editingDraftIndex + 1} 步，改完后点「保存修改」`"
            style="margin-bottom:12px"
          />
          <p class="defaults-hint">默认：重试 0 次 · 失败终止当前步骤 · 超时 {{ simple.timeout || 3 }}s</p>
          <div class="draft-add-bar">
            <el-button type="primary" @click="appendCurrentAction">
              {{ editingDraftIndex !== null ? '保存修改' : '添加本条到列表' }}
            </el-button>
            <el-button v-if="editingDraftIndex !== null" @click="cancelDraftEdit">取消修改</el-button>
            <el-button v-else @click="appendCurrentActionAndStay">添加并继续配置下一条</el-button>
            <span class="bind-hint">
              {{ editingDraftIndex !== null
                ? '修改仅更新列表中的这一步'
                : '可拖拽左侧 ⋮⋮ 调整顺序；点「改」回填修改，或继续添加多步后点「下一步」' }}
            </span>
          </div>
        </el-form>
      </section>

      <section v-show="wizardStep === 2" class="cse-card">
        <div class="cse-card-title">高级配置 <span class="hint">默认折叠，新手可跳过</span></div>
        <el-collapse v-model="advancedOpen">
          <el-collapse-item title="前置说明 / 备注" name="adv">
            <el-input v-model="simple.remark" type="textarea" :rows="2" placeholder="写入描述，便于同事理解" />
            <el-input
              v-model="simple.description"
              type="textarea"
              :rows="2"
              placeholder="完整描述（可留空，保存时自动生成）"
              style="margin-top:8px"
            />
          </el-collapse-item>
        </el-collapse>
        <div class="preview-box">
          <div class="preview-head">
            <div class="preview-title">将生成步骤预览（共 {{ simpleStepsForSave.length }} 步）</div>
            <span class="preview-lang">JSON</span>
          </div>
          <div class="code-editor" role="region" aria-label="步骤 JSON 预览">
            <div
              v-for="line in stepsPreviewLines"
              :key="line.no"
              class="code-line"
            >
              <span class="code-gutter">{{ line.no }}</span>
              <span class="code-content" v-html="line.html" />
            </div>
            <div v-if="!stepsPreviewLines.length" class="code-empty">暂无步骤</div>
          </div>
        </div>
      </section>

      <div class="cse-nav">
        <el-button v-if="wizardStep > 0" @click="wizardStep -= 1">上一步</el-button>
        <el-button v-if="wizardStep < 2" type="primary" @click="nextWizard">下一步</el-button>
        <el-button v-if="wizardStep === 2" type="primary" :loading="saving" @click="submit">保存入库</el-button>
        <el-button @click="emitCancel">取消</el-button>
      </div>
    </div>

    <!-- 专业模式 -->
    <div v-else class="cse-pro">
      <section class="cse-card">
        <el-form label-position="top">
          <el-form-item label="步骤名称" required :error="errors.name">
            <el-input v-model="proName" maxlength="80" show-word-limit @input="errors.name = ''" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="proDesc" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
      </section>
      <section class="cse-card">
        <div class="section-head">
          <div class="cse-card-title" style="margin:0">自动化执行步骤</div>
          <el-radio-group v-model="editMode" size="small" @change="onProModeChange">
            <el-radio-button value="visual">可视化</el-radio-button>
            <el-radio-button value="raw">原生 JSON</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="editMode === 'visual'" class="visual-layout">
          <aside class="palette">
            <div class="palette-title">步骤库</div>
            <div v-for="group in PRO_PALETTE" :key="group.key" class="palette-group">
              <div class="palette-group-name">{{ group.label }}</div>
              <button
                v-for="item in group.items"
                :key="item.action_key"
                type="button"
                class="palette-item"
                @click="addProStep(item)"
              >{{ item.label }}</button>
            </div>
          </aside>
          <div class="canvas">
            <div v-if="!visualSteps.length" class="canvas-empty">从左侧添加步骤</div>
            <div v-for="(s, i) in visualSteps" :key="s._uid" class="canvas-step">
              <el-tag size="small">{{ s.type }}</el-tag>
              <span class="canvas-step-desc">{{ s.element_name || s.name || s.command || s.expected || '' }}</span>
              <div class="canvas-step-actions">
                <el-button text size="small" @click="movePro(i, -1)">上</el-button>
                <el-button text size="small" @click="movePro(i, 1)">下</el-button>
                <el-button text size="small" type="danger" @click="removePro(i)">删</el-button>
              </div>
            </div>
          </div>
        </div>
        <el-input
          v-else
          v-model="rawContent"
          type="textarea"
          :rows="16"
          class="mono"
          @input="onRawInput"
        />
        <div v-if="errors.steps" class="field-error">{{ errors.steps }}</div>
      </section>
      <div class="cse-nav">
        <el-button type="primary" :loading="saving" @click="submit">保存入库</el-button>
        <el-button @click="emitCancel">取消</el-button>
      </div>
    </div>

    <el-dialog v-model="showTemplates" title="高频模板库" width="480px" append-to-body>
      <div class="tpl-list">
        <button
          v-for="t in TEMPLATES"
          :key="t.id"
          type="button"
          class="tpl-item"
          @click="applyTpl(t.id); showTemplates = false"
        >
          <strong>{{ t.name }}</strong>
          <span>{{ categoryLabel(t.category) }} · {{ t.keywords.join(' / ') }}</span>
        </button>
      </div>
    </el-dialog>

    <el-dialog
      v-model="showPoolPicker"
      :title="poolPickerTitle"
      width="720px"
      append-to-body
      destroy-on-close
      @closed="onPoolPickerClosed"
    >
      <div class="pool-toolbar">
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
        <el-input
          v-model="poolKeyword"
          clearable
          placeholder="搜索控件名称 / 页面 / 定位表达式"
          style="flex:1"
        />
        <el-checkbox
          v-if="isCoordPickMode"
          v-model="poolBoundsOnly"
        >仅坐标定位</el-checkbox>
      </div>
      <el-table
        ref="poolTableRef"
        class="pool-pick-table"
        :data="filteredPoolItems"
        height="360"
        highlight-current-row
        row-key="id"
        :row-class-name="poolRowClassName"
        @row-click="onPoolRowClick"
        @row-dblclick="confirmPoolPick"
      >
        <el-table-column label="" width="52" align="center">
          <template #default="{ row }">
            <span
              class="pool-radio"
              :class="{ checked: selectedPoolId === row.id }"
              role="radio"
              :aria-checked="selectedPoolId === row.id"
              @click.stop="selectPoolRow(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="element_name" label="控件名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="page_name" label="页面" width="110" show-overflow-tooltip />
        <el-table-column label="定位类型" width="100">
          <template #default="{ row }">{{ locatorTypeLabel(row.locator_type) }}</template>
        </el-table-column>
        <el-table-column prop="locator_value" label="定位表达式" min-width="200" show-overflow-tooltip />
      </el-table>
      <p v-if="!filteredPoolItems.length" class="pool-empty">暂无匹配控件，可先在控件库录入坐标定位条目</p>
      <div v-else class="pool-selected-bar" :class="{ active: !!selectedPoolRow }">
        <template v-if="selectedPoolRow">
          已选中：<strong>{{ selectedPoolRow.element_name || '未命名控件' }}</strong>
          <span class="pool-selected-expr">{{ selectedPoolRow.locator_value }}</span>
        </template>
        <template v-else>请点击表格行选择一条控件</template>
      </div>
      <template #footer>
        <el-button @click="showPoolPicker = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedPoolRow" @click="confirmPoolPick()">确认选用</el-button>
      </template>
    </el-dialog>

    <el-alert
      v-if="showGuide"
      class="guide-banner"
      type="info"
      show-icon
      closable
      title="新手：① 填名称 → ② 配置动作并多次「添加本条」组成多步 → ③ 保存入库"
      @close="finishGuide"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { commonStepApi, controlApi } from '@/api'
import {
  CATEGORIES, PLATFORMS, ROBOT_COMMANDS, PRO_PALETTE, TEMPLATES,
  CONDITION_KINDS, UI_MODE_KEY, GUIDE_SEEN_KEY,
  actionsByCategory, getAction, categoryLabel,
  blankSimpleForm, buildStepsContent, parseStepsContentFull,
  formFromRow, applyTemplate, resolveStepsForSave, actionToSteps, inferActionKey,
  suggestTemplatesByName, uniqueName, isHazardousAction, autoDescription,
  conditionLabel, conditionNeedsLocator, conditionNeedsExpected, conditionNeedsVarName, normalizeVarKey
} from '@/config/commonStepCatalog'

const props = defineProps({
  editRow: { type: Object, default: null },
  returnTo: { type: String, default: '' },
  embedded: { type: Boolean, default: false }
})
const emit = defineEmits(['saved', 'cancel'])

const router = useRouter()
let uidSeq = 1

const uiMode = ref(localStorage.getItem(UI_MODE_KEY) || 'simple')
const wizardStep = ref(0)
const saving = ref(false)
const showTemplates = ref(false)
const advancedOpen = ref([])
const showGuide = ref(false)
const editMode = ref('visual')
const visualSteps = ref([])
const draftSteps = ref([])
const editingDraftIndex = ref(null)
const collapsedDraftIds = ref(new Set())
const skipActionDefaults = ref(false)
const dragDraftFromIndex = ref(-1)
const dragDraftUid = ref(null)
const dragDraftRange = ref(null) // { start, end } 整块拖动范围
const rawContent = ref('{"steps":[],"meta":{}}')
const proName = ref('')
const proDesc = ref('')
const existingNames = ref([])
const simple = reactive(blankSimpleForm())
const errors = reactive({ name: '', steps: '' })

const showPoolPicker = ref(false)
const poolItems = ref([])
const poolKeyword = ref('')
const poolPageFilter = ref('')
const poolBoundsOnly = ref(true)
const poolPickMode = ref('locator') // locator | swipe_start | swipe_end | tap
const selectedPoolRow = ref(null)
const poolTableRef = ref(null)

const selectedPoolId = computed(() => selectedPoolRow.value?.id ?? null)

const poolPickerTitle = computed(() => {
  if (poolPickMode.value === 'swipe_start') return '选择起点坐标控件'
  if (poolPickMode.value === 'swipe_end') return '选择终点坐标控件'
  if (poolPickMode.value === 'tap') return '选择坐标控件（回填点击坐标）'
  return '从控件库选择控件'
})

const isCoordPickMode = computed(() =>
  ['swipe_start', 'swipe_end', 'tap', 'swipe'].includes(poolPickMode.value)
)

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
  if (isCoordPickMode.value && poolBoundsOnly.value) {
    list = list.filter(isCoordinateControl)
  }
  const page = poolPageFilter.value.trim()
  if (page) {
    list = list.filter(p => String(p.page_name || '').trim() === page)
  }
  const k = poolKeyword.value.trim().toLowerCase()
  if (!k) return list
  return list.filter(p => {
    const hay = [p.element_name, p.page_name, p.locator_type, p.locator_value]
      .join(' ')
      .toLowerCase()
    return hay.includes(k)
  })
})

const currentActions = computed(() => actionsByCategory(simple.category))
const isHazardous = computed(() => isHazardousAction(simple.action_key))
const nameSuggestions = computed(() => suggestTemplatesByName(simple.name))
const currentAction = computed(() => getAction(simple.action_key))
const invokableCommonNames = computed(() => {
  const self = props.editRow?.name
  return (existingNames.value || []).filter(n => n && n !== self && n !== simple.name)
})
const simpleStepsForSave = computed(() => {
  if (draftSteps.value.length) {
    return draftSteps.value.map(({ _uid, ...rest }) => ({ ...rest, enabled: rest.enabled !== false }))
  }
  try {
    return resolveStepsForSave({ ...simple })
  } catch {
    return []
  }
})

const hasDraftBlocks = computed(() =>
  draftSteps.value.some(s => isDraftFlowHeader(s))
)

const visibleDraftItems = computed(() => {
  const list = draftSteps.value
  const depths = computeDraftDepths(list)
  const hidden = computeDraftHiddenIndices(list, collapsedDraftIds.value)
  const items = []
  for (let i = 0; i < list.length; i++) {
    if (hidden.has(i)) continue
    const step = list[i]
    const collapsedCount = isDraftFlowHeader(step) && collapsedDraftIds.value.has(step._uid)
      ? Math.max(0, findDraftBlockEnd(list, i) - i)
      : 0
    items.push({
      step,
      index: i,
      depth: depths[i] || 0,
      collapsedCount
    })
  }
  return items
})

function findDraftBlockEnd(list, startIdx) {
  const start = list[startIdx]
  if (!start || !isDraftFlowHeader(start)) return -1
  let depth = 1
  for (let i = startIdx + 1; i < list.length; i++) {
    const t = list[i].type
    if (t === 'branch' || t === 'loop' || t === 'else_if' || t === 'elif' || t === 'else') depth += 1
    else if (t === 'end_block') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return -1
}

function isDraftFlowHeader(step) {
  const t = step?.type
  return t === 'branch' || t === 'loop' || t === 'else_if' || t === 'elif' || t === 'else'
}

function computeDraftDepths(list) {
  const depths = []
  let depth = 0
  for (const s of list) {
    if (s.type === 'end_block') {
      depth = Math.max(0, depth - 1)
      depths.push(depth)
      continue
    }
    depths.push(depth)
    if (s.type === 'branch' || s.type === 'loop' || s.type === 'else_if' || s.type === 'elif' || s.type === 'else') {
      depth = Math.min(6, depth + 1)
    }
  }
  return depths
}

function computeDraftHiddenIndices(list, collapsedIds) {
  const hidden = new Set()
  if (!collapsedIds?.size) return hidden
  for (let i = 0; i < list.length; i++) {
    const s = list[i]
    if (!isDraftFlowHeader(s) || !collapsedIds.has(s._uid)) continue
    const end = findDraftBlockEnd(list, i)
    if (end <= i) continue
    for (let j = i + 1; j <= end; j++) hidden.add(j)
  }
  return hidden
}

function toggleDraftBlock(uid) {
  if (uid == null) return
  const next = new Set(collapsedDraftIds.value)
  if (next.has(uid)) next.delete(uid)
  else next.add(uid)
  collapsedDraftIds.value = next
}

function expandAllDraftBlocks() {
  collapsedDraftIds.value = new Set()
}

function collapseAllDraftBlocks() {
  const next = new Set()
  for (const s of draftSteps.value) {
    if (isDraftFlowHeader(s) && s._uid != null) next.add(s._uid)
  }
  collapsedDraftIds.value = next
}

const stepsPreview = computed(() => {
  try {
    return JSON.stringify(simpleStepsForSave.value, null, 2)
  } catch {
    return '[]'
  }
})

const stepsPreviewLines = computed(() => {
  const text = stepsPreview.value || '[]'
  return text.split('\n').map((raw, i) => ({
    no: i + 1,
    html: highlightJsonLine(raw)
  }))
})

function escapePreviewHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/** 简易 JSON 语法高亮（按行），风格接近 VS Code Dark+ */
function highlightJsonLine(line) {
  if (line == null || line === '') return '&nbsp;'
  const keyLine = line.match(/^(\s*)("(?:\\.|[^"\\])*")(\s*:\s*)(.*)$/)
  if (keyLine) {
    const [, sp, key, colon, rest] = keyLine
    return (
      escapePreviewHtml(sp)
      + `<span class="tok-key">${escapePreviewHtml(key)}</span>`
      + escapePreviewHtml(colon)
      + highlightJsonValuePart(rest)
    )
  }
  return highlightJsonValuePart(line)
}

function highlightJsonValuePart(raw) {
  if (!raw) return ''
  let out = ''
  let i = 0
  while (i < raw.length) {
    const ch = raw[i]
    if (ch === '"' || ch === "'") {
      const quote = ch
      let j = i + 1
      while (j < raw.length) {
        if (raw[j] === '\\') { j += 2; continue }
        if (raw[j] === quote) { j += 1; break }
        j += 1
      }
      out += `<span class="tok-str">${escapePreviewHtml(raw.slice(i, j))}</span>`
      i = j
      continue
    }
    if (/[-\d]/.test(ch)) {
      const m = raw.slice(i).match(/^-?\d+(?:\.\d+)?/)
      if (m && (i === 0 || /[\s:,\[\{]/.test(raw[i - 1] || ' '))) {
        out += `<span class="tok-num">${escapePreviewHtml(m[0])}</span>`
        i += m[0].length
        continue
      }
    }
    if (/[a-z]/.test(ch)) {
      const m = raw.slice(i).match(/^(true|false|null)\b/)
      if (m) {
        out += `<span class="tok-bool">${m[0]}</span>`
        i += m[0].length
        continue
      }
    }
    if ('{}[],'.includes(ch)) {
      out += `<span class="tok-punc">${escapePreviewHtml(ch)}</span>`
      i += 1
      continue
    }
    out += escapePreviewHtml(ch)
    i += 1
  }
  return out || '&nbsp;'
}

function fieldHas(name) {
  return (currentAction.value?.fields || []).includes(name)
}

function draftStepTypeLabel(s) {
  const key = inferActionKey(s)
  return getAction(key)?.label || '步骤'
}

/** 动作类型色系：标签 type + 行背景 tone */
function draftStepToneClass(step) {
  const t = step?.type || ''
  if (t === 'branch' || t === 'else_if' || t === 'elif' || t === 'else' || t === 'loop') return 'tone-flow'
  if (t === 'end_block') return 'tone-end'
  if (t === 'wait' || t === 'manual_wait') return 'tone-wait'
  if (['click', 'tap_xy', 'long_press', 'tap_ocr', 'swipe', 'drag_element', 'scroll_to_element'].includes(t)) {
    return 'tone-click'
  }
  if (t === 'input' || t === 'clear_input') return 'tone-input'
  if (t.startsWith('assert_') || t === 'check_anomaly' || t === 'robot_log_assert') return 'tone-assert'
  if (t === 'invoke_common' || t === 'invoke_case') return 'tone-invoke'
  if (t === 'screenshot') return 'tone-shot'
  if (t === 'custom_script' || t === 'shell') return 'tone-script'
  if (['launch', 'force_stop', 'install_apk', 'uninstall_app', 'clear_cache', 'press_key', 'dismiss_popup'].includes(t)) {
    return 'tone-app'
  }
  return 'tone-default'
}

function draftStepDetail(s) {
  if (s.type === 'invoke_common') return s.common_step || ''
  if (s.type === 'screenshot') return s.element_name || s.name || s.remark || s.save_path || ''
  if (s.element_name) return s.element_name
  if (s.swipe_start_name && s.swipe_end_name) return `${s.swipe_start_name} → ${s.swipe_end_name}`
  if (s.app_package) return s.app_package
  if (s.type === 'wait' && s.seconds != null && !s.wait_mode) return `${s.seconds} 秒`
  if (s.remark) return s.remark
  if (s.name) return s.name
  if (s.command) return s.command
  if (s.expected) return s.expected
  if (s.text) return s.text
  if (s.condition) return s.condition
  if (s.locator_value) return s.locator_value
  if (s.key) return s.key
  return ''
}

function draftStepLabel(s) {
  const detail = draftStepDetail(s)
  return detail || draftStepTypeLabel(s)
}

function validateCurrentActionForm() {
  if (!simple.action_key) {
    ElMessage.warning('请选择具体动作')
    return false
  }
  if (fieldHas('common_step')) {
    const name = (simple.common_step || '').trim()
    if (!name) {
      ElMessage.warning('请选择要调用的公共步骤')
      return false
    }
    if (name === simple.name?.trim() || (props.editRow && name === props.editRow.name)) {
      ElMessage.warning('不能引用自身，请选择其他公共步骤')
      return false
    }
    if (simple.input_params_json?.trim()) {
      try {
        JSON.parse(simple.input_params_json)
      } catch {
        ElMessage.warning('入参 JSON 格式不正确')
        return false
      }
    }
  }
  if (fieldHas('step_remark') && !(simple.step_remark || '').trim()) {
    ElMessage.warning('请填写截图名称，便于在步骤列表中区分')
    return false
  }
  if (fieldHas('script') && !simple.script?.trim()) {
    ElMessage.warning('请填写脚本内容')
    return false
  }
  if (fieldHas('condition_kind')) {
    const kind = simple.condition_kind || 'exists'
    if (conditionNeedsLocator(kind) && !(simple.locator_value || '').trim()) {
      ElMessage.warning('请填写判断控件的定位表达式（可点「拾取」回填）')
      return false
    }
    if (conditionNeedsVarName(kind) && !normalizeVarKey(simple.var_name)) {
      ElMessage.warning('请填写变量名，例如 product_id')
      return false
    }
    if (conditionNeedsExpected(kind) && !(simple.expected || '').trim()) {
      ElMessage.warning(conditionNeedsVarName(kind) ? '请填写期望值，例如 AX17' : '请填写文本包含的期望文案')
      return false
    }
    if (kind === 'custom' && !(simple.condition || '').trim()) {
      ElMessage.warning('请填写自定义判断条件')
      return false
    }
    if (conditionNeedsVarName(kind)) {
      simple.var_name = normalizeVarKey(simple.var_name)
    }
    simple.condition = conditionLabel(kind, simple.condition, {
      var_name: simple.var_name,
      expected: simple.expected
    })
  }
  if (fieldHas('swipe_coords') && simple.coords_mode === 'pool') {
    if (!simple.swipe_start_name || !simple.swipe_end_name) {
      ElMessage.warning('滑动请分别选择起点、终点两个坐标控件')
      return false
    }
  }
  return true
}

function appendCurrentAction() {
  if (!validateCurrentActionForm()) return false
  const built = actionToSteps({ ...simple })
  if (!built.length) {
    ElMessage.warning('当前动作未生成有效步骤')
    return false
  }
  if (editingDraftIndex.value !== null) {
    const i = editingDraftIndex.value
    const uid = draftSteps.value[i]?._uid
    draftSteps.value[i] = {
      ...built[0],
      enabled: built[0].enabled !== false,
      _uid: uid ?? uidSeq++
    }
    editingDraftIndex.value = null
    delete simple._multiSteps
    ElMessage.success(`已更新第 ${i + 1} 步`)
    return true
  }
  for (const s of built) draftSteps.value.push(withUid({ ...s, enabled: s.enabled !== false }))
  delete simple._multiSteps
  ElMessage.success(`已加入列表，当前共 ${draftSteps.value.length} 步`)
  return true
}

function editDraftStep(i) {
  const step = draftSteps.value[i]
  if (!step) return
  const key = inferActionKey(step)
  const action = getAction(key)
  const keepName = simple.name
  const keepDesc = simple.description
  const keepRemark = simple.remark
  const keepPlatform = simple.platform
  const filled = formFromRow({
    name: keepName,
    description: keepDesc,
    steps_content: JSON.stringify({
      steps: [{ ...step, _uid: undefined }],
      meta: {
        category: action?.category || simple.category || 'atomic',
        platform: keepPlatform || 'any',
        action_key: key || 'click',
        ui_mode: 'simple'
      }
    })
  })
  // 避免 action_key 变更时 watch 用默认值冲掉回填数据
  skipActionDefaults.value = true
  Object.assign(simple, blankSimpleForm(), filled, {
    name: keepName,
    description: keepDesc,
    remark: keepRemark,
    platform: keepPlatform || filled.platform || 'any',
    action_key: key || filled.action_key,
    category: action?.category || filled.category || simple.category
  })
  // 再按步骤字段补一轮，确保定位/等待等原始值可见
  if (step.timeout != null) simple.timeout = step.timeout
  if (step.seconds != null) simple.seconds = step.seconds
  if (step.duration_ms != null) simple.duration_ms = step.duration_ms
  if (step.text != null) simple.text = step.text
  if (step.locator_type) simple.locator_type = step.locator_type
  if (step.locator_value != null) simple.locator_value = step.locator_value
  if (step.element_name != null) simple.element_name = step.element_name
  if (step.display_name != null) simple.display_name = step.display_name
  if (step.pool_id != null) simple.pool_id = step.pool_id
  if (step.condition_kind) simple.condition_kind = step.condition_kind
  if (step.condition != null) simple.condition = step.condition
  if (step.expected != null) simple.expected = step.expected
  if (step.branch_true != null) simple.branch_true = step.branch_true
  if (step.branch_false != null) simple.branch_false = step.branch_false
  if (step.block_type) simple.block_type = step.block_type
  if (step.loop_count != null) simple.loop_count = step.loop_count
  if (step.loop_body != null) simple.loop_body = step.loop_body
  if (step.common_step != null) simple.common_step = step.common_step
  if (step.script != null) simple.script = step.script
  if (step.type === 'screenshot') {
    simple.step_remark = step.element_name || step.name || step.remark || ''
  }
  if (step.remark && step.type === 'else') simple.note = step.remark
  editingDraftIndex.value = i
  nextTick(() => { skipActionDefaults.value = false })
  ElMessage.info(`已回填第 ${i + 1} 步，修改后点「保存修改」`)
}

function cancelDraftEdit() {
  editingDraftIndex.value = null
  ElMessage.info('已取消修改')
}

function appendCurrentActionAndStay() {
  if (editingDraftIndex.value !== null) {
    if (!appendCurrentAction()) return
    return
  }
  if (!appendCurrentAction()) return
  // 保留大类/适用端/动作类型，重置定位等便于继续配下一条
  const keepKey = simple.action_key
  skipActionDefaults.value = true
  applyActionDefaults(keepKey)
  nextTick(() => { skipActionDefaults.value = false })
  simple.coords_mode = 'manual'
  simple.pool_id = null
  simple.element_name = ''
  simple.step_remark = ''
  simple.swipe_start_name = ''
  simple.swipe_end_name = ''
  simple.swipe_start_pool_id = null
  simple.swipe_end_pool_id = null
  simple.swipe_start_locator = ''
  simple.swipe_end_locator = ''
  simple.locator_value = ''
}

function removeDraftStep(i) {
  if (editingDraftIndex.value === i) editingDraftIndex.value = null
  else if (editingDraftIndex.value !== null && editingDraftIndex.value > i) editingDraftIndex.value -= 1
  draftSteps.value.splice(i, 1)
}

function findDraftBlockStart(list, endIdx) {
  if (!list[endIdx] || list[endIdx].type !== 'end_block') return -1
  let depth = 1
  for (let i = endIdx - 1; i >= 0; i--) {
    const t = list[i].type
    if (t === 'end_block') depth += 1
    else if (t === 'branch' || t === 'loop' || t === 'else_if' || t === 'elif' || t === 'else') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return -1
}

/** 拖 if/else if/else/while 或其结束块时，整块一起移动 */
function getDraftMoveRange(list, index) {
  const step = list[index]
  if (!step) return { start: index, end: index }
  if (isDraftFlowHeader(step)) {
    const end = findDraftBlockEnd(list, index)
    if (end >= index) return { start: index, end }
  }
  if (step.type === 'end_block') {
    const start = findDraftBlockStart(list, index)
    if (start >= 0) return { start, end: index }
  }
  return { start: index, end: index }
}

function isDraftDraggingIndex(index) {
  const r = dragDraftRange.value
  return !!(r && index >= r.start && index <= r.end)
}

function onDraftDragStart(e, index) {
  const range = getDraftMoveRange(draftSteps.value, index)
  dragDraftFromIndex.value = range.start
  dragDraftRange.value = range
  dragDraftUid.value = draftSteps.value[range.start]?._uid ?? null
  try { e.dataTransfer.setData('text/plain', String(range.start)) } catch { /* ignore */ }
  e.dataTransfer.effectAllowed = 'move'
}

function onDraftDragOver(e) {
  e.dataTransfer.dropEffect = 'move'
}

function onDraftDrop(e, toIndex) {
  const range = dragDraftRange.value || getDraftMoveRange(draftSteps.value, dragDraftFromIndex.value)
  const fromStart = range.start
  const fromEnd = range.end
  if (fromStart < 0 || toIndex < 0) return
  // 拖到自身块内部：忽略
  if (toIndex >= fromStart && toIndex <= fromEnd) return

  const arr = [...draftSteps.value]
  const count = fromEnd - fromStart + 1
  const chunk = arr.splice(fromStart, count)
  let insertAt = toIndex
  if (toIndex > fromEnd) insertAt = toIndex - count
  arr.splice(insertAt, 0, ...chunk)
  draftSteps.value = arr
  remapEditingDraftIndex(fromStart, fromEnd, insertAt, count)
}

function remapEditingDraftIndex(fromStart, fromEnd, insertAt, count) {
  const edit = editingDraftIndex.value
  if (edit == null) return
  if (edit >= fromStart && edit <= fromEnd) {
    editingDraftIndex.value = insertAt + (edit - fromStart)
    return
  }
  let next = edit
  if (edit > fromEnd) next -= count
  if (next >= insertAt) next += count
  editingDraftIndex.value = next
}

function onDraftDragEnd() {
  dragDraftFromIndex.value = -1
  dragDraftUid.value = null
  dragDraftRange.value = null
}

function clearDraftSteps() {
  draftSteps.value = []
  editingDraftIndex.value = null
  collapsedDraftIds.value = new Set()
}

function ensureDraftStepsReady() {
  if (draftSteps.value.length) return true
  // 未点「添加本条」时，允许把当前表单当作唯一一步
  if (!validateCurrentActionForm()) return false
  const built = actionToSteps({ ...simple })
  if (!built.length) {
    ElMessage.warning('请至少添加一条子步骤')
    return false
  }
  for (const s of built) draftSteps.value.push(withUid({ ...s, enabled: s.enabled !== false }))
  return true
}

function withUid(step) {
  return { ...step, _uid: uidSeq++ }
}

function onUiModeChange(mode) {
  localStorage.setItem(UI_MODE_KEY, mode)
  if (mode === 'pro') syncSimpleToPro()
  else syncProToSimple()
}

function onCategoryChange() {
  const list = actionsByCategory(simple.category)
  if (!list.find(a => a.key === simple.action_key)) {
    simple.action_key = list[0]?.key || ''
    applyActionDefaults(simple.action_key)
  }
}

function applyActionDefaults(key) {
  const a = getAction(key)
  if (!a?.defaults) return
  Object.assign(simple, a.defaults)
}

watch(() => simple.action_key, (k, prev) => {
  if (skipActionDefaults.value) return
  if (k && k !== prev) applyActionDefaults(k)
})

function onNameInput() {
  errors.name = simple.name?.trim() ? '' : '请填写步骤名称'
}

function applyTpl(id) {
  const next = applyTemplate(id, { ...simple })
  Object.assign(simple, next)
  if (next._multiSteps?.length) {
    simple._multiSteps = next._multiSteps
    draftSteps.value = next._multiSteps.map(s => withUid({ ...s, enabled: s.enabled !== false }))
  } else {
    delete simple._multiSteps
  }
  ElMessage.success(`已套用模板`)
}

function onConditionKindChange(kind) {
  if (kind !== 'custom') {
    simple.condition = conditionLabel(kind, simple.condition, {
      var_name: simple.var_name,
      expected: simple.expected
    })
  }
}

function nextWizard() {
  if (wizardStep.value === 0) {
    if (!simple.name?.trim()) {
      errors.name = '请填写步骤名称'
      ElMessage.warning('请填写步骤名称')
      return
    }
    if (!simple.category || !simple.platform) {
      ElMessage.warning('请选择类型与适用端')
      return
    }
  }
  if (wizardStep.value === 1) {
    if (!ensureDraftStepsReady()) return
  }
  wizardStep.value += 1
}

function syncSimpleToPro() {
  proName.value = simple.name
  proDesc.value = simple.description || autoDescription(simple)
  const steps = simpleStepsForSave.value
  visualSteps.value = steps.map(withUid)
  rawContent.value = buildStepsContent({ ...simple, ui_mode: 'pro' }, steps)
}

function syncProToSimple() {
  simple.name = proName.value || simple.name
  simple.description = proDesc.value
  const parsed = parseStepsContentFull(rawContent.value)
  if (parsed.ok) {
    Object.assign(simple, formFromRow({
      name: proName.value,
      description: proDesc.value,
      steps_content: rawContent.value
    }))
    draftSteps.value = (parsed.steps || []).map(withUid)
  }
}

function addProStep(item) {
  const action = getAction(item.action_key)
  if (action) {
    const form = { ...blankSimpleForm(), action_key: action.key, ...action.defaults }
    const steps = action.toSteps(form)
    for (const s of steps) visualSteps.value.push(withUid({ ...s, enabled: s.enabled !== false }))
    if (action.key === 'branch_if' || action.key === 'loop_while') {
      ElMessage.info('已插入条件块：请切换「原生 JSON」补充 locator_value 等判断参数，或改用简易模式配置')
    }
  } else {
    visualSteps.value.push(withUid({ type: item.type, enabled: true, ...(item.defaults || {}) }))
  }
  syncProRaw()
}

function movePro(i, d) {
  const t = i + d
  if (t < 0 || t >= visualSteps.value.length) return
  const arr = visualSteps.value
  ;[arr[i], arr[t]] = [arr[t], arr[i]]
  syncProRaw()
}

function removePro(i) {
  visualSteps.value.splice(i, 1)
  syncProRaw()
}

function syncProRaw() {
  const steps = visualSteps.value.map(({ _uid, ...rest }) => rest)
  rawContent.value = JSON.stringify({
    steps,
    meta: {
      category: simple.category,
      platform: simple.platform,
      action_key: simple.action_key,
      ui_mode: 'pro'
    }
  }, null, 2)
}

function onProModeChange(mode) {
  if (mode === 'visual') {
    const parsed = parseStepsContentFull(rawContent.value)
    if (!parsed.ok) {
      ElMessage.warning(parsed.error || 'JSON 有误')
      editMode.value = 'raw'
      return
    }
    visualSteps.value = parsed.steps.map(withUid)
  } else {
    syncProRaw()
  }
}

function onRawInput() {
  const parsed = parseStepsContentFull(rawContent.value)
  errors.steps = parsed.ok ? '' : (parsed.error || '解析失败')
}

function goPicker() {
  const returnTo = props.returnTo || router.currentRoute.value.fullPath
  router.push({ path: '/element-picker', query: { returnTo, forCommonStep: '1' } })
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

function locatorTypeLabel(type) {
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
  poolPickMode.value = mode
  poolKeyword.value = ''
  poolPageFilter.value = ''
  selectedPoolRow.value = null
  poolBoundsOnly.value = ['swipe_start', 'swipe_end', 'tap', 'swipe'].includes(mode)
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

function selectPoolRow(row) {
  selectedPoolRow.value = row || null
  nextTick(() => {
    poolTableRef.value?.setCurrentRow?.(row || undefined)
  })
}

function onPoolRowClick(row) {
  selectPoolRow(row)
}

function poolRowClassName({ row }) {
  return selectedPoolRow.value?.id === row?.id ? 'is-pool-selected' : ''
}

function onPoolPickerClosed() {
  selectedPoolRow.value = null
}

function pointFromBounds(bounds) {
  return {
    x: Math.round((bounds.x1 + bounds.x2) / 2),
    y: Math.round((bounds.y1 + bounds.y2) / 2)
  }
}

function confirmPoolPick(row) {
  const item = row && row.locator_value !== undefined ? row : selectedPoolRow.value
  if (!item) {
    ElMessage.warning('请先选择一条控件')
    return
  }
  const mode = poolPickMode.value
  if (mode === 'locator') {
    simple.locator_type = item.locator_type || simple.locator_type || 'id'
    simple.locator_value = item.locator_value || ''
    simple.element_name = item.element_name || simple.element_name
    simple.pool_id = item.id
    showPoolPicker.value = false
    ElMessage.success(`已选用控件「${item.element_name || item.locator_value}」`)
    return
  }

  const bounds = parseBounds(item.locator_value)
  if (!bounds) {
    ElMessage.warning('该控件不是可解析的坐标格式，请选择坐标定位（如 [x1,y1][x2,y2] 或 x,y）')
    return
  }
  const name = item.element_name || item.locator_value || '坐标控件'
  const pt = pointFromBounds(bounds)

  if (mode === 'swipe_start') {
    simple.coords_mode = 'pool'
    simple.swipe_start_name = name
    simple.swipe_start_pool_id = item.id
    simple.swipe_start_locator = item.locator_value || ''
    simple.x1 = pt.x
    simple.y1 = pt.y
    syncSwipeElementName()
    showPoolPicker.value = false
    ElMessage.success(`已选择起点「${name}」`)
    return
  }
  if (mode === 'swipe_end') {
    simple.coords_mode = 'pool'
    simple.swipe_end_name = name
    simple.swipe_end_pool_id = item.id
    simple.swipe_end_locator = item.locator_value || ''
    simple.x2 = pt.x
    simple.y2 = pt.y
    syncSwipeElementName()
    showPoolPicker.value = false
    ElMessage.success(`已选择终点「${name}」`)
    return
  }
  if (mode === 'tap') {
    simple.coords_mode = 'pool'
    simple.element_name = name
    simple.pool_id = item.id
    simple.locator_type = item.locator_type || 'bounds'
    simple.locator_value = item.locator_value || ''
    simple.x = pt.x
    simple.y = pt.y
    showPoolPicker.value = false
    ElMessage.success(`已选用坐标控件「${name}」`)
  }
}

function syncSwipeElementName() {
  const a = simple.swipe_start_name
  const b = simple.swipe_end_name
  if (a && b) simple.element_name = `${a} → ${b}`
  else if (a) simple.element_name = `起点：${a}`
  else if (b) simple.element_name = `终点：${b}`
  else simple.element_name = ''
}

function useManualCoords() {
  simple.coords_mode = 'manual'
}

function clearSwipePoint(which) {
  if (which === 'start') {
    simple.swipe_start_name = ''
    simple.swipe_start_pool_id = null
    simple.swipe_start_locator = ''
  } else {
    simple.swipe_end_name = ''
    simple.swipe_end_pool_id = null
    simple.swipe_end_locator = ''
  }
  syncSwipeElementName()
  if (!simple.swipe_start_name && !simple.swipe_end_name) {
    simple.coords_mode = 'manual'
  }
}

function clearPoolCoords() {
  simple.coords_mode = 'manual'
  simple.pool_id = null
  simple.element_name = ''
  simple.locator_type = 'id'
  simple.locator_value = ''
  simple.swipe_start_name = ''
  simple.swipe_end_name = ''
  simple.swipe_start_pool_id = null
  simple.swipe_end_pool_id = null
  simple.swipe_start_locator = ''
  simple.swipe_end_locator = ''
}

function emitCancel() {
  emit('cancel')
}

function finishGuide() {
  showGuide.value = false
  localStorage.setItem(GUIDE_SEEN_KEY, '1')
}

function loadRow(row) {
  errors.name = ''
  errors.steps = ''
  wizardStep.value = 0
  editingDraftIndex.value = null
  collapsedDraftIds.value = new Set()
  if (!row) {
    Object.assign(simple, blankSimpleForm())
    delete simple._multiSteps
    proName.value = ''
    proDesc.value = ''
    visualSteps.value = []
    draftSteps.value = []
    rawContent.value = '{"steps":[],"meta":{}}'
    return
  }
  Object.assign(simple, formFromRow(row))
  delete simple._multiSteps
  proName.value = row.name || ''
  proDesc.value = row.description || ''
  const parsed = parseStepsContentFull(row.steps_content || '{}')
  const steps = parsed.steps || []
  visualSteps.value = steps.map(withUid)
  draftSteps.value = steps.map(withUid)
  rawContent.value = JSON.stringify({
    steps,
    meta: parsed.meta || {}
  }, null, 2)
  if (parsed.meta?.ui_mode === 'pro') uiMode.value = 'pro'
}

async function loadExistingNames() {
  try {
    const list = (await commonStepApi.list()).data || []
    existingNames.value = list.map(s => s.name).filter(Boolean)
  } catch {
    existingNames.value = []
  }
}

async function submit() {
  let name = ''
  let description = ''
  let stepsContent = ''
  let hazardous = false

  if (uiMode.value === 'simple') {
    name = simple.name?.trim()
    if (!name) {
      errors.name = '请填写步骤名称'
      wizardStep.value = 0
      ElMessage.warning('请填写步骤名称')
      return
    }
    if (!simple.action_key && !draftSteps.value.length) {
      wizardStep.value = 1
      ElMessage.warning('请选择动作并添加子步骤')
      return
    }
    if (!ensureDraftStepsReady()) {
      wizardStep.value = 1
      return
    }
    hazardous = draftSteps.value.some(s =>
      ['custom_script', 'shell'].includes(s.type) || isHazardousAction(inferActionKey(s))
    ) || isHazardous.value
    const names = existingNames.value.filter(n => !props.editRow || n !== props.editRow.name)
    if (names.includes(name)) {
      const renamed = uniqueName(name, names)
      try {
        await ElMessageBox.confirm(
          `名称「${name}」已存在，是否自动重命名为「${renamed}」？`,
          '重名校验',
          { type: 'warning', confirmButtonText: '使用新名称', cancelButtonText: '返回修改' }
        )
        name = renamed
        simple.name = renamed
      } catch {
        return
      }
    }
    description = simple.description?.trim() || autoDescription(simple)
    if (simple.remark) description = `${description}；${simple.remark}`
    const steps = simpleStepsForSave.value
    if (!steps.length) {
      ElMessage.warning('未生成可执行步骤，请先添加子步骤')
      wizardStep.value = 1
      return
    }
    stepsContent = buildStepsContent({ ...simple, ui_mode: 'simple', name }, steps)
  } else {
    name = proName.value?.trim()
    if (!name) {
      errors.name = '请填写步骤名称'
      ElMessage.warning('请填写步骤名称')
      return
    }
    if (editMode.value === 'visual') syncProRaw()
    const parsed = parseStepsContentFull(rawContent.value)
    if (!parsed.ok) {
      errors.steps = parsed.error
      ElMessage.warning(parsed.error || '步骤脚本错误')
      return
    }
    hazardous = parsed.steps.some(s =>
      ['custom_script', 'shell'].includes(s.type)
    )
    description = proDesc.value || ''
    stepsContent = JSON.stringify({
      steps: parsed.steps,
      meta: {
        ...(parsed.meta || {}),
        ui_mode: 'pro',
        category: parsed.meta?.category || simple.category,
        platform: parsed.meta?.platform || simple.platform
      }
    }, null, 2)
  }

  if (hazardous) {
    try {
      await ElMessageBox.confirm(
        '该步骤包含自定义脚本 / Shell / 接口调用等高级能力，保存后将立刻对全平台可用。请确认内容安全。',
        '高危步骤确认',
        { type: 'warning', confirmButtonText: '确认保存上架', cancelButtonText: '返回检查' }
      )
    } catch {
      return
    }
  }

  saving.value = true
  try {
    const payload = { name, description, steps_content: stepsContent }
    if (props.editRow?.id) {
      await commonStepApi.update(props.editRow.id, payload)
      ElMessage.success('公共步骤已更新')
      emit('saved', { name, id: props.editRow.id, updated: true })
    } else {
      const res = await commonStepApi.create({ ...payload, status: 'active' })
      ElMessage.success(hazardous ? '高危步骤已入库可用（请知会测试负责人抽查）' : '公共步骤已保存入库')
      const id = res?.data?.id
      let wentToCase = false
      try {
        await ElMessageBox.confirm('是否立即在用例中引用该步骤？', '立即引用', {
          confirmButtonText: '去用例编辑器',
          cancelButtonText: '稍后',
          type: 'success'
        })
        wentToCase = true
        if (props.returnTo) {
          const path = String(props.returnTo).split('?')[0]
          router.push({ path, query: { invokeCommon: name } })
        } else {
          router.push({ path: '/cases/editor', query: { invokeCommon: name } })
        }
      } catch { /* skip */ }
      emit('saved', { name, id, updated: false, wentToCase })
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

watch(() => props.editRow, (row) => loadRow(row), { immediate: true })

onMounted(async () => {
  await loadExistingNames()
  const tpl = router.currentRoute.value.query.template
  if (!props.editRow && typeof tpl === 'string' && tpl) {
    applyTpl(tpl)
  }
  if (!props.editRow && !localStorage.getItem(GUIDE_SEEN_KEY) && uiMode.value === 'simple') {
    await nextTick()
    setTimeout(() => { showGuide.value = true }, 400)
  }
})

defineExpose({ loadRow, submit })
</script>

<style scoped>
.cse { display: flex; flex-direction: column; gap: 14px; }
.cse-toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
}
.cse-toolbar-right { display: flex; gap: 8px; }
.cse-steps { margin-bottom: 8px; }
.cse-card {
  padding: 16px 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
.cse-card-title {
  font-weight: 700; margin-bottom: 12px; font-size: 15px;
}
.hint { font-weight: 400; color: #94a3b8; font-size: 12px; margin-left: 8px; }
.draft-steps-panel {
  margin-bottom: 14px;
  padding: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.draft-steps-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  gap: 8px;
}
.draft-drag-hint {
  margin-right: auto;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 400;
}
.draft-steps-head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.draft-steps-empty {
  color: #94a3b8;
  font-size: 13px;
  padding: 10px 4px;
}
.draft-steps-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 约 10 行：行高 ~38px + 间距 6px */
  max-height: calc(10 * 44px);
  overflow-y: auto;
  padding-right: 4px;
}
.draft-step-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: grab;
}
.draft-step-row:active { cursor: grabbing; }
.draft-step-row.is-dragging { opacity: 0.55; }
.draft-drag-handle {
  flex-shrink: 0;
  width: 16px;
  color: #94a3b8;
  font-size: 12px;
  letter-spacing: -1px;
  cursor: grab;
  user-select: none;
  line-height: 1;
}
.draft-drag-handle:active { cursor: grabbing; }
.draft-step-row.editing {
  border-color: var(--atp-primary, #8B6CF0);
  background: #f5f3ff;
  box-shadow: 0 0 0 1px rgba(139, 108, 240, 0.2);
}
.draft-step-row.is-block {
  border-left: 3px solid var(--atp-accent, #6366f1);
  background: #fff7ed;
  border-color: #fed7aa;
}
.draft-step-row.is-nested {
  background: #f8fafc;
}
.draft-step-row.is-end {
  border-left: 3px dashed #cbd5e1;
  color: #64748b;
}
/* 只给动作标签上色，行底色保持原样 */
.draft-type-tag.tone-click {
  color: #1d4ed8 !important;
  border-color: #93c5fd !important;
  background: #dbeafe !important;
}
.draft-type-tag.tone-input {
  color: #4338ca !important;
  border-color: #a5b4fc !important;
  background: #e0e7ff !important;
}
.draft-type-tag.tone-wait {
  color: #0369a1 !important;
  border-color: #7dd3fc !important;
  background: #e0f2fe !important;
}
.draft-type-tag.tone-assert {
  color: #15803d !important;
  border-color: #86efac !important;
  background: #dcfce7 !important;
}
.draft-type-tag.tone-flow {
  color: #c2410c !important;
  border-color: #fdba74 !important;
  background: #ffedd5 !important;
}
.draft-type-tag.tone-end {
  color: #64748b !important;
  border-color: #cbd5e1 !important;
  background: #f1f5f9 !important;
}
.draft-type-tag.tone-invoke {
  color: #7e22ce !important;
  border-color: #d8b4fe !important;
  background: #f3e8ff !important;
}
.draft-type-tag.tone-shot {
  color: #047857 !important;
  border-color: #6ee7b7 !important;
  background: #d1fae5 !important;
}
.draft-type-tag.tone-script {
  color: #b91c1c !important;
  border-color: #fca5a5 !important;
  background: #fee2e2 !important;
}
.draft-type-tag.tone-app {
  color: #b45309 !important;
  border-color: #fcd34d !important;
  background: #fef3c7 !important;
}
.draft-type-tag.tone-default {
  color: #475569 !important;
  border-color: #cbd5e1 !important;
  background: #f1f5f9 !important;
}
.draft-block-toggle {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border: 1px solid #c7d2fe;
  border-radius: 5px;
  background: #eef2ff;
  color: var(--atp-accent, #6366f1);
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
  padding: 0;
}
.draft-block-toggle:hover {
  background: #e0e7ff;
}
.draft-block-spacer {
  flex-shrink: 0;
  width: 20px;
}
.draft-step-idx {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 50%;
  background: #e2e8f0;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}
.draft-step-desc {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.draft-step-actions { flex-shrink: 0; display: flex; }
.draft-add-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.bind-hint { font-size: 12px; color: #94a3b8; }
.opt-hint { float: right; color: #94a3b8; font-size: 12px; }
.suggest-row { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.suggest-label { font-size: 12px; color: #64748b; }
.suggest-tag { cursor: pointer; }
.locator-row { display: flex; gap: 8px; width: 100%; }
.coords { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.coords.labeled label {
  color: var(--atp-text-secondary, #64748b);
  font-size: 12px;
  white-space: nowrap;
}
.coords-block { width: 100%; }
.coords-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.coords-picked {
  font-size: 12px;
  color: var(--atp-primary, #8B6CF0);
}
.pool-control-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}
.pool-control-card.filled {
  background: rgba(139, 108, 240, 0.08);
  border: 1px solid rgba(139, 108, 240, 0.35);
}
.pool-control-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.pool-control-tag {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--atp-primary, #8B6CF0);
  background: #fff;
  border: 1px solid rgba(139, 108, 240, 0.35);
  border-radius: 999px;
  padding: 2px 8px;
}
.pool-control-name {
  font-size: 14px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pool-control-placeholder {
  font-size: 13px;
  color: #94a3b8;
}
.pool-control-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.swipe-pool-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.swipe-pool-arrow {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1;
  letter-spacing: 0.02em;
}
.coords-arrow {
  color: var(--atp-text-secondary, #94a3b8);
  margin: 0 4px;
}
.pool-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.pool-empty {
  margin: 12px 0 0;
  color: var(--atp-text-secondary, #94a3b8);
  font-size: 13px;
  text-align: center;
}
.pool-selected-bar {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
}
.pool-selected-bar.active {
  background: rgba(139, 108, 240, 0.1);
  color: #334155;
  border: 1px solid rgba(139, 108, 240, 0.35);
}
.pool-selected-bar strong {
  color: var(--atp-primary, #8B6CF0);
  margin-right: 8px;
}
.pool-selected-expr {
  color: #64748b;
  word-break: break-all;
}
.pool-pick-table :deep(.el-table__row) {
  cursor: pointer;
}
.pool-pick-table :deep(.el-table__row.is-pool-selected > td.el-table__cell) {
  background: rgba(139, 108, 240, 0.16) !important;
}
.pool-pick-table :deep(.el-table__row.is-pool-selected > td.el-table__cell:first-child) {
  box-shadow: inset 3px 0 0 var(--atp-primary, #8B6CF0);
}
.pool-pick-table :deep(.current-row > td.el-table__cell) {
  background: rgba(139, 108, 240, 0.12) !important;
}
.pool-radio {
  display: inline-flex;
  width: 16px;
  height: 16px;
  border: 2px solid #cbd5e1;
  border-radius: 50%;
  box-sizing: border-box;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
}
.pool-radio.checked {
  border-color: var(--atp-primary, #8B6CF0);
  background: #fff;
}
.pool-radio.checked::after {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--atp-primary, #8B6CF0);
}
.defaults-hint { font-size: 12px; color: #94a3b8; margin: 0; }
.preview-box {
  margin-top: 14px;
  background: #1e1e1e;
  color: #d4d4d4;
  border: 1px solid #333;
  border-radius: 10px;
  padding: 0;
  max-height: 520px;
  min-height: 280px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  background: #252526;
  border-bottom: 1px solid #333;
  flex-shrink: 0;
}
.preview-title {
  font-size: 12px;
  color: #9cdcfe;
  margin: 0;
  font-weight: 600;
}
.preview-lang {
  font-size: 11px;
  color: #6a9955;
  font-family: ui-monospace, Consolas, 'Courier New', monospace;
  padding: 2px 8px;
  border-radius: 4px;
  background: #1e1e1e;
  border: 1px solid #3c3c3c;
}
.code-editor {
  flex: 1;
  overflow: auto;
  padding: 8px 0 12px;
  font-family: ui-monospace, 'Cascadia Code', Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  line-height: 1.65;
  tab-size: 2;
}
.code-line {
  display: flex;
  min-height: 20px;
  padding-right: 14px;
}
.code-line:hover {
  background: rgba(255, 255, 255, 0.04);
}
.code-gutter {
  flex-shrink: 0;
  width: 44px;
  padding: 0 10px 0 0;
  text-align: right;
  color: #858585;
  user-select: none;
  border-right: 1px solid #333;
  margin-right: 12px;
}
.code-content {
  flex: 1;
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #d4d4d4;
}
.code-empty {
  padding: 24px;
  text-align: center;
  color: #6a6a6a;
  font-size: 12px;
}
.code-content :deep(.tok-key) { color: #9cdcfe; }
.code-content :deep(.tok-str) { color: #ce9178; }
.code-content :deep(.tok-num) { color: #b5cea8; }
.code-content :deep(.tok-bool) { color: #569cd6; }
.code-content :deep(.tok-punc) { color: #d4d4d4; }
.cse-nav { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }
.mono :deep(textarea) { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.field-error { color: #dc2626; font-size: 12px; margin-top: 6px; }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.visual-layout { display: grid; grid-template-columns: 240px 1fr; gap: 14px; min-height: 420px; }
.palette {
  background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 10px; overflow: auto; max-height: 560px;
}
.palette-title { font-weight: 600; font-size: 13px; margin-bottom: 8px; }
.palette-group { margin-bottom: 10px; }
.palette-group-name { font-size: 12px; color: #64748b; margin-bottom: 4px; }
.palette-item {
  display: block; width: 100%; text-align: left; border: 1px solid #e2e8f0;
  background: #f8fafc; border-radius: 6px; padding: 6px 8px; margin-bottom: 4px;
  cursor: pointer; font-size: 12px;
}
.palette-item:hover { border-color: #93c5fd; background: #eff6ff; }
.canvas {
  background: #fff; border: 1px dashed #cbd5e1; border-radius: 8px;
  padding: 10px; overflow: auto; max-height: 560px; min-height: 420px;
}
.canvas-empty { color: #94a3b8; text-align: center; padding: 40px 12px; }
.canvas-step {
  display: flex; align-items: center; gap: 8px; padding: 8px;
  border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 6px;
}
.canvas-step-desc { flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.canvas-step-actions { display: flex; }
.tpl-list { display: flex; flex-direction: column; gap: 8px; }
.tpl-item {
  text-align: left; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 10px 12px; background: #fff; cursor: pointer;
}
.tpl-item:hover { border-color: #93c5fd; background: #f8fafc; }
.tpl-item strong { display: block; margin-bottom: 4px; }
.tpl-item span { font-size: 12px; color: #64748b; }
.guide-banner { margin-bottom: 4px; }
</style>
