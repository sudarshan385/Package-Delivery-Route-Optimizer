# UI Design System & Component Documentation — Package Delivery Route Optimizer

This document provides a comprehensive specification of the existing user interface design system, navigation hierarchy, page views, reusable components, forms, tables, map visualization, and responsive design conventions.

> **Design Preservation Policy**: This document captures the verified, existing UI design system. The UI is not being redesigned.

---

## 1. Design System & Token Foundation

The design system follows a **Strict Flat Design** with **high developer and productivity density**, clear contrast ratios, and restrained elevation.

### 1.1 Color Tokens & Theming (`frontend/src/index.css`)

The application supports both Dark (default) and Light themes via CSS custom properties applied to the root element. Theme toggling updates `data-theme` on `<html>` and persists the selection in `localStorage` under the key `'app-theme'`.

#### Dark Theme Tokens (Default)
| Token Name | Value | Purpose |
| :--- | :--- | :--- |
| `--theme-bg` | `#0B0F14` | Deep charcoal / near-black canvas background |
| `--theme-surface` | `#121721` | Card surfaces, modals, top navigation bar |
| `--theme-elevated` | `#1A2230` | Form inputs, table header rows, secondary card wells |
| `--theme-text` | `#F1F5F9` | High-contrast body text and headers |
| `--theme-muted` | `#94A3B8` | Subtitles, labels, secondary metadata, inactive icons |
| `--theme-border` | `#232D3F` | Clean perimeter card and table borders |

#### Light Theme Tokens (`[data-theme='light']`)
| Token Name | Value | Purpose |
| :--- | :--- | :--- |
| `--theme-bg` | `#F8FAFC` | Light slate canvas background |
| `--theme-surface` | `#FFFFFF` | Clean white card surfaces and modals |
| `--theme-elevated` | `#F1F5F9` | Well backgrounds, input field fills |
| `--theme-text` | `#0F172A` | Deep slate high-contrast text |
| `--theme-muted` | `#64748B` | Secondary text and muted labels |
| `--theme-border` | `#E2E8F0` | Subtle light border dividers |

#### Brand & Status Accent Colors
| Token Name | Hex Code | Purpose / Semantic Context |
| :--- | :--- | :--- |
| `--color-primary` | `#10B981` | Emerald Green brand accent, active routes, primary buttons |
| `--color-accent` | `#059669` | Hover state for primary actions |
| `--color-success` | `#10B981` | Completed deliveries, available resources, active accounts |
| `--color-warning` | `#F59E0B` | In-progress deliveries, fallback non-road edges, alerts |
| `--color-danger` | `#EF4444` | Failed/cancelled deliveries, destructive actions, error badges |
| `--color-info` | `#3B82F6` | Assigned deliveries, hub node markers, general info tags |

#### Spacing & Typography Scale
- **Base Font**: `Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`
- **Base Size**: `14px` (`html { font-size: 14px; }`), body line-height `1.5`
- **Header Scale**:
  - `h1`: `20px` (font-weight: 700, line-height: 1.3, letter-spacing: -0.3px)
  - `h2`: `16px` (font-weight: 600, line-height: 1.35, letter-spacing: -0.2px)
  - `h3`: `14px` (font-weight: 600, line-height: 1.4)
- **Border Radius**:
  - `--radius-button`: `8px`
  - `--radius-card`: `12px`
  - `--radius-input`: `6px`
- **Spacing Scale**:
  - `--space-1` (4px), `--space-2` (8px), `--space-3` (12px), `--space-4` (16px), `--space-5` (24px), `--space-6` (32px), `--space-7` (48px), `--space-8` (64px)

---

## 2. Navigation Architecture (`Navbar.jsx`)

The top navigation bar provides desktop horizontal navigation, role-based link filtering, theme switching, mobile drawer support, and a user profile dropdown.

### 2.1 Role-Based NavLink Matrix

The `navLinks` specification dynamically filters visible navigation items based on `user.role`:

```javascript
export const navLinks = [
  { to: '/', label: 'Dashboard', roles: ['ADMIN', 'DRIVER', 'USER'], end: true },
  { to: '/packages', label: 'Packages', roles: ['ADMIN'] },
  { to: '/packages', label: 'My Packages', roles: ['USER'] },
  { to: '/locations', label: 'Locations', roles: ['ADMIN'] },
  { to: '/drivers', label: 'Drivers', roles: ['ADMIN'] },
  { to: '/vehicles', label: 'Vehicles', roles: ['ADMIN'] },
  { to: '/deliveries', label: 'Deliveries', roles: ['ADMIN'] },
  { to: '/deliveries', label: 'My Deliveries', roles: ['DRIVER'] },
  { to: '/deliveries', label: 'Track Delivery', roles: ['USER'] },
  { to: '/routes', label: 'Routes', roles: ['ADMIN', 'DRIVER'] },
  { to: '/analytics', label: 'Analytics', roles: ['ADMIN'] },
  { to: '/reports', label: 'Reports', roles: ['ADMIN'] },
  { to: '/ai-optimization', label: 'AI Optimization', roles: ['ADMIN'] },
  { to: '/users', label: 'Users', roles: ['ADMIN'] },
];
```

### 2.2 Navigation Bar Controls
1. **Brand Identity**: Left-aligned Route icon + "Route Optimizer" brand title linking to `/`.
2. **Horizontal Nav Links**: Highlight active route using `NavLink` with border-bottom indicator and emerald green text.
3. **Theme Toggle**: Icon button with `Sun` / `Moon` to switch dark and light modes.
4. **User Profile Dropdown**:
   - Shows user avatar / fallback letter icon and display name.
   - Dropdown displays full name, email, role badge (`ADMIN`, `DRIVER`, `USER`).
   - Profile link (`/profile`) allowing users to rotate their password.
   - Logout button that clears JWT, terminates auth state, and navigates to `/`.
5. **Mobile Hamburger Menu**:
   - Toggles full-width collapsible drawer on screens `< 768px`.
   - Listens for `Escape` key to dismiss automatically.
   - Automatically collapses on route changes.

---

## 3. Page Layouts & Specifications

### 3.1 Public Pages

#### 1. `Homepage.jsx` (Route: `/`)
- Rendered when unauthenticated.
- Hero showcase featuring platform purpose: deterministic Dijkstra route optimization, real road geometry, and atomic package lifecycle tracking.
- Call-to-action buttons for Login (`/login`) and Register (`/register`).
- Summary of core system features and technology stack badges.

#### 2. `Login.jsx` (Route: `/login`)
- Authenticates existing users (`ADMIN`, `DRIVER`, `USER`).
- Accepts either username or registered email address.
- Validates non-empty password.
- Issues JWT token upon success and redirects to `/` (authenticated Dashboard).
- Includes links to Registration (`/register`) and initial Setup (`/setup`).

#### 3. `Register.jsx` (Route: `/register`)
- Public registration endpoint for non-admin accounts.
- Dropdown role selector supporting `USER` (Customer) and `DRIVER`.
- Conditional field: When `DRIVER` is selected, an mandatory **Driver License Number** field is displayed.
- Submits to `POST /api/auth/register`, automatically logs in the user, and redirects to Dashboard.

#### 4. `Setup.jsx` (Route: `/setup`)
- Initial administrator bootstrap page.
- Checks `GET /api/auth/setup/status`. If setup has already been completed, access is blocked and the user is redirected to `/login`.
- Collects Admin username, email, full name, and password.
- Submits to `POST /api/auth/setup`, acquiring the backend PostgreSQL advisory lock.

---

### 3.2 Authenticated Pages

#### 5. `Dashboard.jsx` (Route: `/`)
- Dynamically adapts content based on user role:
  - **ADMIN View**: Full fleet & business overview. High-level KPI stat cards (Total Packages, Active Deliveries, Available Drivers, Vehicles In Use, Routes Calculated, Total Distance). Pie charts and bar graphs for delivery statuses and package priorities. Recent activity log.
  - **DRIVER View**: Scoped to deliveries assigned to the current driver. Displays Assigned, In-Progress, and Completed counts. Quick-access cards to open active delivery route maps.
  - **USER View**: Scoped to packages and deliveries owned by the customer. Tracking search input, package status breakdown, and recent orders.

#### 6. `Packages.jsx` (Route: `/packages`)
- **Allowed Roles**: `ADMIN`, `USER`.
- **List View**: Paginated table showing Tracking Number, Sender, Receiver, Destination, Weight, Priority Badge, Status Badge (`PENDING`, `IN_DELIVERY`, `DELIVERED`, `FAILED`), and Created Date.
- **Filters**: Filter by Package Status and Priority.
- **Creation Modal**:
  - Direct Node Mode: Select from pre-existing active Locations.
  - City/Custom Address Mode: Enter City, Pickup Address, and Delivery Address. Integrates with Nominatim geocoding to resolve addresses into coordinate points.
  - Generates tracking number automatically if omitted.

#### 7. `Locations.jsx` (Route: `/locations`)
- **Allowed Roles**: `ADMIN`.
- **Top Actions**: "Add Location" and "Add Edge" modal triggers.
- **Pending Approvals Banner**: High-priority alert banner listing customer-submitted package destinations awaiting administrative graph integration.
- **Approval Modal ("Approve & Connect")**: Links a pending customer location to an existing active hub/depot node. Invokes OSRM to automatically calculate incoming/outgoing road distances, transit times, and road geometries.
- **Network Map**: Interactive Leaflet map displaying all active nodes (Hubs: purple, Depots: orange, Customers: green) and road segments.
- **Locations Table**: Name, City, Address, Lat/Lng coordinates, Location Type, Active status, and Edit/Deactivate action buttons.
- **Roads & Graph Edges Table (`#roads`)**: Source Node, Destination Node, Physical Distance (km), Estimated Transit Time (min), Active status, and Deactivate buttons.

#### 8. `Drivers.jsx` (Route: `/drivers`)
- **Allowed Roles**: `ADMIN`.
- **Driver Fleet Table**: Driver Name, Phone Number, License Number, Operational Availability (`AVAILABLE`, `ON_DELIVERY`, `UNAVAILABLE`), and Account Status.
- **Creation/Edit Modal**: Register new drivers with name, phone, license number, and initial status.
- **Safety**: Deactivation modal with confirmation guard (`ConfirmDialog`).

#### 9. `Vehicles.jsx` (Route: `/vehicles`)
- **Allowed Roles**: `ADMIN`.
- **Vehicle Fleet Table**: Registration Plate Number, Vehicle Type Badge (`BIKE`, `CAR`, `VAN`, `TRUCK`), Max Payload Capacity (kg), Operational Status (`AVAILABLE`, `IN_USE`, `MAINTENANCE`, `OFFLINE`), and Active status.
- **Creation/Edit Modal**: Plate number, type selector, capacity in kg, and status.

#### 10. `Deliveries.jsx` (Route: `/deliveries`)
- **Allowed Roles**: `ADMIN`, `DRIVER`, `USER`.
- **Delivery Table**: Delivery ID (`#id`), Package Tracking Number, Pickup Location, Delivery Location, Assigned Driver, Assigned Vehicle, Status Badge, and Last Updated Timestamp.
- **Role Actions**:
  - `ADMIN`: "New Delivery" modal (schedules package in `PENDING` status); "Assign" modal (assigns driver and vehicle using pessimistic locking); "Cancel" delivery action.
  - `DRIVER`: "Start Delivery" (`IN_PROGRESS`); "Complete Delivery" (`COMPLETED`); "Fail Delivery" (`FAILED`).
  - `USER`: Read-only view showing status, timestamps, and route milestones for own orders.

#### 11. `RouteOptimizer.jsx` (Route: `/routes`)
- **Allowed Roles**: `ADMIN`, `DRIVER`.
- **Interactive Route Planning**:
  - Select Source Location and Destination Location.
  - Select Optimization Criterion: **Shortest Distance** (`SHORTEST_DISTANCE`) or **Fastest Route** (`FASTEST_ROUTE`).
  - Optional Delivery Link: Associate optimization directly with an active delivery.
- **Metrics Display**: Total Distance (km), Authoritative Travel Time (minutes), Route Stop Order, Traversed Edges.
- **Interactive Map**: Highlights the calculated Dijkstra route with Leaflet. Renders solid green lines for real OSRM road geometry, or dashed amber lines for fallback straight segments. Displays bottom-right geometry badge.

#### 12. `Analytics.jsx` (Route: `/analytics`)
- **Allowed Roles**: `ADMIN`.
- **Filters**: Start Date and End Date range selector.
- **Visualizations**:
  - Metric summary cards (Total Volume, Success Rate, Average Transit Time, Total Distance).
  - Delivery Volume Trend (Recharts Line/Area Chart).
  - Delivery Status Distribution (Recharts Pie Chart).
  - Fleet Utilization Rate (Recharts Bar Chart).

#### 13. `Reports.jsx` (Route: `/reports`)
- **Allowed Roles**: `ADMIN`.
- **CSV Audit Exports**:
  - **Delivery Operations Audit Report**: Downloads complete delivery lifecycle CSV (`/api/reports/deliveries/csv`).
  - **Route Optimization Audit Report**: Downloads complete calculated route paths, stops, distances, and travel times CSV (`/api/reports/routes/csv`).
- Displays sample CSV schema preview and timestamp metadata.

#### 14. `AIOptimization.jsx` (Route: `/ai-optimization`)
- **Allowed Roles**: `ADMIN`.
- **DDQN Research Dashboard**: Displays results from the isolated Double Deep Q-Network experiment (`/api/experiments/ddqn/latest`).
- **Research Method Flow Diagram**: Amazon Dataset $\rightarrow$ TSM-TW $\rightarrow$ i-DeepRoute $\rightarrow$ State Representation $\rightarrow$ DDQN + APER $\rightarrow$ CMR + ED $\rightarrow$ Evaluation.
- **Metrics Tables**: Experiment Overview (seed, stops, route counts), DDQN Configuration (episodes, learning rate, decay, batch size), Training Results (updates, final epsilon, rewards), and Test Evaluation (CMR, Edit Distance).
- **Dijkstra Comparison Status**: Explains why Dijkstra baseline comparison is unavailable for the Amazon Last Mile dataset.
- **Interactive Dynamic Replanning Demo (`DynamicReplanningDemo`)**: Sandbox allowing operators to simulate a dynamic order insertion at $t > 0$ and visualize revised route stops on Leaflet map without touching production data.

#### 15. `Users.jsx` (Route: `/users`)
- **Allowed Roles**: `ADMIN`.
- **User Directory Table**: User ID, Username, Email, Full Name, Role Badge (`ADMIN`, `DRIVER`, `USER`), and Account Active status.
- **User Creation Modal**: Creates accounts directly. For `DRIVER` accounts, automatically enforces driver profile creation with license number.

#### 16. `Profile.jsx` (Route: `/profile`)
- **Allowed Roles**: `ADMIN`, `DRIVER`, `USER`.
- Displays current user account information (ID, username, email, full name, role).
- **Password Rotation Form**: Secure self-service password update requiring current password verification before updating. Calls `PUT /api/users/profile/password`.

---

## 4. Reusable UI Component Library (`frontend/src/components/ui/`)

### 4.1 `Badge.jsx`
Status pills with standardized colors and dark/light mode compatibility:
- `StatusBadge({ status })`: Supports `CREATED`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`, `PENDING`, `IN_DELIVERY`, `DELIVERED`, `AVAILABLE`, `ON_DELIVERY`, `IN_USE`, `MAINTENANCE`.
- `PriorityBadge({ priority })`: Color-coded for `LOW` (gray), `MEDIUM` (blue), `HIGH` (amber), `URGENT` (red).
- `RoleBadge({ role })`: `ADMIN` (purple/emerald), `DRIVER` (blue), `USER` (slate).
- `ActiveBadge({ active })`: `ACTIVE` (green), `INACTIVE` (gray/red).

### 4.2 `Button.jsx`
- **Variants**: `primary` (emerald solid), `secondary` (subtle border/elevated fill), `danger` (red solid/border), `ghost` (transparent hover).
- **Sizes**: `sm`, `md`, `lg`.
- **States**: `loading` (disables button and shows spinning SVG loader), `disabled`.
- **Icon Support**: Accepts Lucide React icon components via `icon` prop.

### 4.3 `Card.jsx`
- Surfaces structured operational content.
- Supports `title`, `subtitle`, and `action` slot in the header.
- Provides standard padding and perimeter border matching `--theme-border`.

### 4.4 `ConfirmDialog.jsx`
- Accessible confirmation modal for destructive operations (e.g. deactivating a driver, vehicle, or location edge).
- Supports `title`, `message`, `confirmLabel`, `danger` styling, and `loading` state.

### 4.5 `EmptyState.jsx`
- Standardized placeholder when tables or lists contain zero records.
- Renders a centered Lucide icon, explanatory title, description, and optional action button.

### 4.6 `ErrorState.jsx`
- Displays error alert card with retry button (`onRetry`).
- Exports `parseApiError(error, defaultMessage)` helper to extract RFC 7807 problem details or Axios error strings.

### 4.7 `Input.jsx` & `Select.jsx`
- Form inputs styled with `--radius-input` and `--theme-border`.
- Features accessible label, required asterisk, validation error state, and helper text.
- Supports `readOnly` and `disabled` states with visual feedback.

### 4.8 `Modal.jsx`
- Accessible dialog rendering via React portal or fixed overlay.
- Closes on backdrop click or `Escape` key press.
- Structured with header (title and close `X` button), scrollable body, and action footer.

### 4.9 `PageHeader.jsx`
- Standard top banner on every view.
- Left side: Page `title` (h1) and `subtitle`.
- Right side: Action button slot (e.g. "Add Driver", "New Delivery").

### 4.10 `Pagination.jsx`
- Accessible numbered pagination control for Spring Data `Page` responses.
- Displays current page, total pages, total elements, and Previous/Next buttons.

### 4.11 `Table.jsx`
- Modular component set: `Table`, `TableHead`, `TableBody`, `TableRow`, `TableHeaderCell`, `TableCell`.
- Implements hover row highlights, clean cell padding, and horizontal scroll wrapper.

---

## 5. Map Integration Architecture (`Map.jsx`)

The mapping component is built on **React Leaflet** (Leaflet 1.9.4) rendering OpenStreetMap tiles.

### 5.1 Marker Pinning & Types
Location coordinates are mapped to Leaflet divIcons with distinct color-coded markers:
- **HUB**: Purple marker (`#8b5cf6`)
- **DEPOT**: Amber / Orange marker (`#f59e0b`)
- **CUSTOMER**: Emerald Green marker (`#10b981`)
- **Default**: Brand Emerald (`#10B981`)
- **Popup**: Displays location name, physical address, location type, and latitude/longitude formatted to 5 decimal places.

### 5.2 Multi-Segment Road Geometry Rendering
The component supports two visual layers:
1. **Background Network Graph**: Subdued gray lines (`weight: 1.5 - 2.5`, `opacity: 0.35 - 0.65`) showing all active road edges between vertices.
2. **Active Dijkstra Route**: Bold polyline (`weight: 5`, `opacity: 0.9`) tracing the optimal path.
   - **Real OSRM Road Geometry**: If `geometryGeojson` (GeoJSON `LineString`) is present on the route edges or matching network edges, coordinates are parsed and drawn along the physical road path in brand green (`var(--color-primary)`).
   - **Fallback Geometry**: If geometry is absent, drawn as a dashed amber polyline connecting the two endpoints directly.
3. **Geometry Status Indicator Badge**: Positioned at bottom-right of the map container, indicating how many segments use real OSRM road curves versus fallback straight connections.
4. **Auto-Fit Bounds (`FitBounds`)**: Automatically adjusts zoom level and map center so all active markers and route polylines are comfortably framed with a 40px padding.

---

## 6. Forms, Tables & Responsive Conventions

### 6.1 Form Guidelines
- All interactive forms reside in accessible modal overlays (`Modal.jsx`) or structured cards.
- Multi-field inputs use CSS grid layout:
  ```css
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  ```
- On screens narrower than 640px, form grids automatically collapse to a single column (`1fr`).
- Every form field requires unique `id` and `htmlFor` pairings for accessibility and browser testing.

### 6.2 Table Conventions
- All table instances are wrapped in a container with `overflow-x: auto;` to prevent layout breaks on small viewports.
- Monospace font is enforced on technical data: Tracking numbers, License numbers, Vehicle registration plates, and Coordinates.
- Numeric metrics (Distance, Time, Payload) are right-aligned.
- Actions column is right-aligned with button spacing (`display: inline-flex; gap: 6px;`).

### 6.3 Responsive Breakpoints
- **Mobile (< 768px)**: Navbar collapses to hamburger drawer. PageHeader stacks title and action button vertically. Forms collapse to single column. Tables enable touch-scroll.
- **Tablet (768px - 1024px)**: Top navbar displays primary links. Stat cards display in a 2x2 grid.
- **Desktop (> 1024px)**: Full horizontal navbar. Dashboard stat cards display in 4-column layout (`repeat(auto-fit, minmax(240px, 1fr))`). Side-by-side scenario cards on Route Optimizer and AI Demo.
