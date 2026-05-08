import { ref } from 'vue';
import DashboardView from './views/DashboardView.vue';
import HelpDocPanel from './components/HelpDocPanel.vue';
const dashboardRef = ref(null);
const activeSection = ref('tasks');
const showHelpDoc = ref(false);
function jumpTo(section) {
    activeSection.value = section;
    dashboardRef.value?.scrollToSection(section);
}
function openCreateTask() {
    activeSection.value = 'tasks';
    dashboardRef.value?.scrollToSection('tasks');
    dashboardRef.value?.openCreateDialog();
}
function openHelpDoc() {
    showHelpDoc.value = true;
}
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "app-shell" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.aside, __VLS_intrinsicElements.aside)({
    ...{ class: "left-rail" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "brand-mark" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.nav, __VLS_intrinsicElements.nav)({
    ...{ class: "rail-nav" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.jumpTo('overview');
        } },
    ...{ class: "rail-item" },
    ...{ class: ({ active: __VLS_ctx.activeSection === 'overview' }) },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.jumpTo('tasks');
        } },
    ...{ class: "rail-item" },
    ...{ class: ({ active: __VLS_ctx.activeSection === 'tasks' }) },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.jumpTo('config');
        } },
    ...{ class: "rail-item" },
    ...{ class: ({ active: __VLS_ctx.activeSection === 'config' }) },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.jumpTo('detail');
        } },
    ...{ class: "rail-item" },
    ...{ class: ({ active: __VLS_ctx.activeSection === 'detail' }) },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.jumpTo('monitor');
        } },
    ...{ class: "rail-item" },
    ...{ class: ({ active: __VLS_ctx.activeSection === 'monitor' }) },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.main, __VLS_intrinsicElements.main)({
    ...{ class: "workspace" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.header, __VLS_intrinsicElements.header)({
    ...{ class: "global-topbar" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "topbar-actions" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.openHelpDoc) },
    ...{ class: "ghost" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.openCreateTask) },
    ...{ class: "primary" },
});
/** @type {[typeof DashboardView, ]} */ ;
// @ts-ignore
const __VLS_0 = __VLS_asFunctionalComponent(DashboardView, new DashboardView({
    ref: "dashboardRef",
}));
const __VLS_1 = __VLS_0({
    ref: "dashboardRef",
}, ...__VLS_functionalComponentArgsRest(__VLS_0));
/** @type {typeof __VLS_ctx.dashboardRef} */ ;
var __VLS_3 = {};
var __VLS_2;
if (__VLS_ctx.showHelpDoc) {
    /** @type {[typeof HelpDocPanel, ]} */ ;
    // @ts-ignore
    const __VLS_5 = __VLS_asFunctionalComponent(HelpDocPanel, new HelpDocPanel({
        ...{ 'onClose': {} },
    }));
    const __VLS_6 = __VLS_5({
        ...{ 'onClose': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_5));
    let __VLS_8;
    let __VLS_9;
    let __VLS_10;
    const __VLS_11 = {
        onClose: (...[$event]) => {
            if (!(__VLS_ctx.showHelpDoc))
                return;
            __VLS_ctx.showHelpDoc = false;
        }
    };
    var __VLS_7;
}
/** @type {__VLS_StyleScopedClasses['app-shell']} */ ;
/** @type {__VLS_StyleScopedClasses['left-rail']} */ ;
/** @type {__VLS_StyleScopedClasses['brand-mark']} */ ;
/** @type {__VLS_StyleScopedClasses['rail-nav']} */ ;
/** @type {__VLS_StyleScopedClasses['rail-item']} */ ;
/** @type {__VLS_StyleScopedClasses['rail-item']} */ ;
/** @type {__VLS_StyleScopedClasses['rail-item']} */ ;
/** @type {__VLS_StyleScopedClasses['rail-item']} */ ;
/** @type {__VLS_StyleScopedClasses['rail-item']} */ ;
/** @type {__VLS_StyleScopedClasses['workspace']} */ ;
/** @type {__VLS_StyleScopedClasses['global-topbar']} */ ;
/** @type {__VLS_StyleScopedClasses['topbar-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['primary']} */ ;
// @ts-ignore
var __VLS_4 = __VLS_3;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            DashboardView: DashboardView,
            HelpDocPanel: HelpDocPanel,
            dashboardRef: dashboardRef,
            activeSection: activeSection,
            showHelpDoc: showHelpDoc,
            jumpTo: jumpTo,
            openCreateTask: openCreateTask,
            openHelpDoc: openHelpDoc,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
