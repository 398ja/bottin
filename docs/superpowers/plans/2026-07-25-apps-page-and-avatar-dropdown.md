# Apps Page and Avatar Dropdown — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the post-login landing page from `/search` to `/apps` and add an avatar dropdown with Profile and Logout options.

**Architecture:** Add a new Spring MVC controller for `/apps` and a corresponding empty Thymeleaf content fragment. Modify the existing navigation fragment to wrap the avatar in a dropdown toggle with a plain-CSS dropdown menu. Update the login redirect in `login.html` and `router.html`.

**Tech Stack:** Spring Boot 3.4, Thymeleaf, HTMX, plain CSS

## Global Constraints

- Keep existing nav links (Search, Settings) unchanged
- No new JS dependencies — plain CSS/HTML dropdown
- Follow existing patterns in controllers and templates

---

### Task 1: Create AppsController

**Files:**
- Create: `bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/AppsController.java`

**Interfaces:**
- Consumes: nothing from earlier tasks
- Produces: `GET /apps` → renders `layout` template with `content = "apps"` and `title = "Apps"`

- [ ] **Step 1: Create `AppsController.java`**

```java
package xyz.tcheeric.bottin.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppsController {

    @GetMapping("/apps")
    public String appsPage(Model model) {
        model.addAttribute("title", "Apps");
        model.addAttribute("content", "apps");
        return "layout";
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `mvn compile -pl bottin-client-ui -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add bottin-client-ui/src/main/java/xyz/tcheeric/bottin/client/controller/AppsController.java
git commit -m "feat: add AppsController for /apps page"
```

---

### Task 2: Create apps.html template placeholder

**Files:**
- Create: `bottin-client-ui/src/main/resources/templates/apps.html`

**Interfaces:**
- Consumes: controller from Task 1 sets `content = "apps"`
- Produces: an empty content fragment rendered inside `layout.html`

- [ ] **Step 1: Create `apps.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Bottin - Apps</title>
</head>
<body>
    <div th:fragment="content" class="container">
        <div class="card">
            <h1 style="font-size: 1.25rem; margin-bottom: 1rem;">Apps</h1>
            <p class="text-secondary">Apps will appear here.</p>
        </div>
    </div>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add bottin-client-ui/src/main/resources/templates/apps.html
git commit -m "feat: add apps.html placeholder template"
```

---

### Task 3: Update login redirects

**Files:**
- Modify: `bottin-client-ui/src/main/resources/templates/login.html` (lines 92, 115)

**Interfaces:**
- Consumes: nothing
- Produces: after successful login, user goes to `/apps` instead of `/search`

- [ ] **Step 1: Change nsec paste mode redirect (line 92)**

Change:
```javascript
.then(function() { window.location.href = '/search'; })
```
To:
```javascript
.then(function() { window.location.href = '/apps'; })
```

- [ ] **Step 2: Change passphrase unlock redirect (line 115)**

Change:
```javascript
window.location.href = '/search';
```
To:
```javascript
window.location.href = '/apps';
```

- [ ] **Step 3: Commit**

```bash
git add bottin-client-ui/src/main/resources/templates/login.html
git commit -m "feat: redirect to /apps after successful login"
```

---

### Task 4: Update router redirect

**Files:**
- Modify: `bottin-client-ui/src/main/resources/templates/router.html` (line 20)

**Interfaces:**
- Consumes: nothing
- Produces: session check redirects to `/apps` instead of `/search`

- [ ] **Step 1: Change session redirect (line 20)**

Change:
```javascript
window.location.replace('/search');
```
To:
```javascript
window.location.replace('/apps');
```

- [ ] **Step 2: Commit**

```bash
git add bottin-client-ui/src/main/resources/templates/router.html
git commit -m "feat: redirect to /apps from router when session exists"
```

---

### Task 5: Add avatar dropdown to nav

**Files:**
- Modify: `bottin-client-ui/src/main/resources/templates/fragments/nav.html`
- Modify: `bottin-client-ui/src/main/resources/templates/layout.html`
- Modify: `bottin-client-ui/src/main/resources/static/css/styles.css` (append dropdown styles)

**Interfaces:**
- Consumes: avatar image from `userAvatar` model attribute (or default)
- Produces: click-to-toggle dropdown with Profile and Logout items

- [ ] **Step 1: Replace avatar link with dropdown in `nav.html`**

Replace lines 10-12:
```html
            <a th:href="@{/profile}" class="nav-link">
                <img src="/img/default-avatar.png" alt="" class="nav-avatar" th:src="${userAvatar} ?: '/img/default-avatar.png'">
            </a>
```
With:
```html
            <div class="nav-dropdown">
                <button class="nav-link nav-dropdown-toggle" onclick="this.parentElement.classList.toggle('open')">
                    <img src="/img/default-avatar.png" alt="" class="nav-avatar" th:src="${userAvatar} ?: '/img/default-avatar.png'">
                </button>
                <div class="nav-dropdown-menu">
                    <a th:href="@{/profile}" class="nav-dropdown-item">Profile</a>
                    <a class="nav-dropdown-item"
                       href="#"
                       onclick="event.preventDefault();"
                       hx-post="/api/v1/auth/logout"
                       hx-confirm="This will clear your session."
                       hx-target="body"
                       hx-push-url="/login">Logout</a>
                </div>
            </div>
```

Also remove the old standalone Logout link (lines 13-19):
```html
            <a class="nav-link"
               href="#"
               onclick="event.preventDefault();"
               hx-post="/api/v1/auth/logout"
               hx-confirm="This will clear your session."
               hx-target="body"
               hx-push-url="/login">Logout</a>
```

The final `nav.html` should be:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <nav th:fragment="nav" class="nav">
        <a th:href="@{/}" class="nav-brand">Bottin</a>
        <div class="nav-spacer"></div>
        <div th:if="${#httpServletRequest?.userPrincipal != null}">
            <a th:href="@{/search}" class="nav-link">Search</a>
            <a th:href="@{/settings}" class="nav-link">Settings</a>
            <div class="nav-dropdown">
                <button class="nav-link nav-dropdown-toggle" onclick="this.parentElement.classList.toggle('open')">
                    <img src="/img/default-avatar.png" alt="" class="nav-avatar" th:src="${userAvatar} ?: '/img/default-avatar.png'">
                </button>
                <div class="nav-dropdown-menu">
                    <a th:href="@{/profile}" class="nav-dropdown-item">Profile</a>
                    <a class="nav-dropdown-item"
                       href="#"
                       onclick="event.preventDefault();"
                       hx-post="/api/v1/auth/logout"
                       hx-confirm="This will clear your session."
                       hx-target="body"
                       hx-push-url="/login">Logout</a>
                </div>
            </div>
        </div>
    </nav>
</body>
</html>
```

- [ ] **Step 2: Add dropdown CSS to `styles.css`**

Append to `styles.css`:

```css
.nav-dropdown {
    position: relative;
    display: inline-block;
}

.nav-dropdown-toggle {
    cursor: pointer;
    background: none;
    border: none;
    padding: 0;
    line-height: 0;
}

.nav-dropdown-menu {
    display: none;
    position: absolute;
    right: 0;
    top: calc(100% + 0.5rem);
    min-width: 160px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    box-shadow: 0 4px 12px oklch(0 0 0 / 0.12);
    z-index: 100;
    overflow: hidden;
}

.nav-dropdown.open .nav-dropdown-menu {
    display: block;
}

.nav-dropdown-item {
    display: block;
    padding: 0.625rem 1rem;
    font-size: 0.875rem;
    color: var(--color-text);
    text-decoration: none;
    transition: background 0.15s;
    cursor: pointer;
}

.nav-dropdown-item:hover {
    background: var(--color-bg);
    text-decoration: none;
}

.nav-dropdown-item:last-child {
    border-top: 1px solid var(--color-border);
    color: var(--color-danger);
}
```

- [ ] **Step 3: Close dropdown on outside click**

Modify `bottin-client-ui/src/main/resources/templates/layout.html` — add this script block before `</body>`:

```html
<script>
    document.addEventListener('click', function(e) {
        document.querySelectorAll('.nav-dropdown.open').forEach(function(d) {
            if (!d.contains(e.target)) d.classList.remove('open');
        });
    });
</script>
```

- [ ] **Step 4: Verify build**

Run: `mvn compile -pl bottin-client-ui -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add bottin-client-ui/src/main/resources/templates/fragments/nav.html \
      bottin-client-ui/src/main/resources/templates/layout.html \
      bottin-client-ui/src/main/resources/static/css/styles.css
git commit -m "feat: add avatar dropdown with Profile and Logout to nav"
```
