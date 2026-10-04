# PHASE 2 - TASK 2.1: DateField UI Component Enhancement
## Implementation Plan Presentation

**Status**: PLAN PRESENTATION (AWAITING IMPLEMENTATION APPROVAL)
**Date**: October 4, 2026
**Owner**: UI React Developer Agent
**Effort**: 0.5 day
**Priority**: P2 (Medium)

---

## 1. TASK SUMMARY

Fix the DateField component to provide better user guidance for date input formatting. Currently, HTML5 `<input type="date">` does not support custom placeholder text, creating inconsistent UX compared to other filter components.

---

## 2. DETAILED SPECIFICATION

### Current State Analysis
- **Component File**: `workflow-orchestrator-dashboard/src/components/common/Inputs.jsx` (lines 52-65)
- **Component Type**: React functional component with floating label pattern
- **Problem**: HTML5 date input ignores placeholder attribute, shows browser-native format picker
- **Usage Location**: `src/pages/PipelineListPage.jsx` (lines 291-292)
  - "Created From" date filter
  - "Created To" date filter
- **Filter Grid Layout**: 7-column responsive grid (6px gaps, 8px horizontal padding)

### Current DateField Implementation
```javascript
export function DateField({ label, value, onChange, placeholder = 'Pick a date' }) {
  const [focused, setFocused] = useState(false);
  return (
    <Field label={label} placeholder={placeholder} showPlaceholder={focused && !value} active={focused || !!value}>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
    </Field>
  );
}
```

### Design System Context
- **Primary Color**: Ocean Blue (#0ea5e9, #0284c7)
- **Text Colors**: 
  - Primary: #e2eaf2
  - Muted: #6b8094 (field labels)
  - Dim: #3f566a (placeholders)
- **Background**: Surface-2 (#122035)
- **Font**: Inter, 10-11px for inputs
- **Transitions**: 0.12s ease for opacity/position
- **Border Radius**: 7px
- **Focus Shadow**: 0 0 0 2px rgba(14,165,233,.25)

### Date Format & Data Flow
1. User selects date via `<input type="date">` → returns "YYYY-MM-DD" format (e.g., "2025-01-15")
2. Component stores in state: `filters.createdFrom = "2025-01-15"`
3. Before API call, converts to instant: `toInstant("2025-01-15", false)` → `"2025-01-15T00:00:00.000Z"`
4. API query param: `?createdFrom=2025-01-15T00:00:00.000Z&createdTo=2025-01-15T23:59:59.999Z`
5. Backend filters: `WHERE date_created >= ... AND date_created <= ...`

---

## 3. PROPOSED SOLUTION

### Approach: Helper Text Below Input (RECOMMENDED)
Add a small format hint text below the date input that appears on focus and when input is empty.

### HTML Structure (New)
```html
<div class="field">
  <label class="field-label">Created From</label>
  <input type="date" value="" />
  <div class="field-helper">Format: YYYY-MM-DD</div>  ← NEW
</div>
```

### CSS Implementation (New)
Add to `src/styles.css` after line 323:

```css
/* Helper text for form fields (e.g., date format hint) */
.field-helper {
  position: absolute;
  left: 10px;
  bottom: -16px;  /* Below input height (40px) */
  color: var(--text-dim);       /* #3f566a */
  font-size: 10px;
  font-weight: 400;
  line-height: 1.2;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.12s ease;
  white-space: nowrap;
}

/* Show helper text when field is focused or empty */
.field:focus-within .field-helper {
  opacity: 1;
}

/* Also show when input has no value and field is active */
.field.helper-visible .field-helper {
  opacity: 1;
}

/* Adjust field wrapper height to accommodate helper text */
.field-with-helper {
  padding-bottom: 18px;  /* Extra space for helper text */
}
```

### React Component Enhancement
Modify `DateField` function in `Inputs.jsx`:

```javascript
export function DateField({ 
  label, 
  value, 
  onChange, 
  placeholder = 'Pick a date',
  helperText = 'Format: YYYY-MM-DD'  // NEW parameter
}) {
  const [focused, setFocused] = useState(false);
  
  return (
    <div className="field-with-helper">  {/* NEW wrapper class */}
      <Field 
        label={label} 
        placeholder={placeholder} 
        showPlaceholder={focused && !value} 
        active={focused || !!value}
      >
        <input
          type="date"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
        />
      </Field>
      {helperText && <div className="field-helper">{helperText}</div>}  {/* NEW helper text */}
    </div>
  );
}
```

### Usage Update (Optional)
In `PipelineListPage.jsx`, can now customize helper text:

```javascript
// Current usage (uses default "Format: YYYY-MM-DD"):
<DateField label="Created From" value={filters.createdFrom} onChange={(value) => updateFilter('createdFrom', value)} />

// Customizable usage (if needed):
<DateField 
  label="Created From" 
  value={filters.createdFrom} 
  onChange={(value) => updateFilter('createdFrom', value)}
  helperText="Format: YYYY-MM-DD"
/>
```

---

## 4. IMPLEMENTATION STEPS

### Phase 1: Code Changes
1. **Update Inputs.jsx** (3 changes)
   - Add `helperText` parameter to DateField signature
   - Add `className="field-with-helper"` wrapper div
   - Render helper text conditionally below input

2. **Update styles.css** (2 changes)
   - Add `.field-helper` styling (opacity, positioning, transition)
   - Add `.field-with-helper` padding adjustment

3. **Optional: Update PipelineListPage.jsx** (1 change)
   - No change required (default helper text works fine)
   - Could add explicit `helperText` prop if custom formatting desired

### Phase 2: Testing
- [ ] Open dashboard in browser
- [ ] Navigate to PipelineListPage
- [ ] Click on "Created From" date field
- [ ] Verify helper text appears ("Format: YYYY-MM-DD")
- [ ] Verify helper text fades on blur
- [ ] Test on mobile (375px), tablet (768px), desktop (1440px)
- [ ] Open Chrome DevTools, check console for errors/warnings
- [ ] Verify no layout shift when helper text appears/disappears
- [ ] Test keyboard navigation (Tab key, screen reader if available)

### Phase 3: Visual Verification
- [ ] Helper text color matches design token (--text-dim #3f566a)
- [ ] Font size matches spec (10px)
- [ ] Spacing below input is correct (~4px visual gap)
- [ ] Transition is smooth (0.12s ease)
- [ ] No overlap with adjacent filter fields
- [ ] Responsive layout maintains at all breakpoints

---

## 5. ACCEPTANCE CRITERIA

✅ **Functionality**
- [ ] Helper text displays "Format: YYYY-MM-DD" by default
- [ ] Helper text appears on focus, fades on blur
- [ ] Helper text component accepts customizable `helperText` prop
- [ ] No changes to date input behavior or value handling

✅ **Visual & UX**
- [ ] Responsive on all viewports (1440px desktop, 768px tablet, 375px mobile)
- [ ] Helper text aligned with input (left: 10px, matching input padding)
- [ ] Helper text color matches design token (#3f566a)
- [ ] Font size and weight correct (10px, 400)
- [ ] Smooth opacity transition (0.12s)
- [ ] No layout shift when helper appears/disappears
- [ ] Visual consistency with other filter components

✅ **Quality**
- [ ] Zero console errors in DevTools
- [ ] Zero console warnings
- [ ] No unhandled exceptions
- [ ] Accessibility verified (keyboard navigation, ARIA if needed)
- [ ] Code follows project conventions (React hooks, functional components)

✅ **Testing**
- [ ] Manual testing on all breakpoints (dev server with Vite)
- [ ] Browser DevTools validation (responsive design mode)
- [ ] Focus/blur behavior verified
- [ ] Empty state and filled state both work correctly

---

## 6. EFFORT BREAKDOWN

| Activity | Time | Notes |
|----------|------|-------|
| Code analysis | 15 min | Review current component, CSS, usage |
| Component modification | 15 min | Update Inputs.jsx (helperText prop, wrapper div, render) |
| CSS styling | 10 min | Add .field-helper and .field-with-helper rules |
| Browser testing | 10 min | Responsive design, console validation |
| Documentation | 5 min | Inline comments, JSDoc if needed |
| **TOTAL** | **0.5 day** | |

---

## 7. RISK MITIGATION

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Breaking other fields using Field wrapper | Low | Medium | Verify SelectField, TextField, SearchBox still work |
| Layout shift causing UX issues | Low | Low | Use absolute positioning + white-space: nowrap |
| Accessibility regression | Low | Medium | Manual tab navigation testing, ARIA review |
| Mobile overflow issues | Low | Low | Test 375px viewport, adjust positioning if needed |

---

## 8. DELIVERABLES

Upon completion:
1. ✅ Modified `Inputs.jsx` with enhanced DateField component
2. ✅ Updated `styles.css` with `.field-helper` styling
3. ✅ Browser testing validation (screenshots if needed)
4. ✅ Zero console errors/warnings
5. ✅ Ready for Reviewer validation

---

## 9. READY FOR APPROVAL

**Awaiting User Confirmation:**
- [ ] Plan is acceptable
- [ ] Approach meets requirements
- [ ] Effort estimate is reasonable
- [ ] Acceptance criteria are clear

**Next Step**: User approves → Implementation begins → Reviewer validates


