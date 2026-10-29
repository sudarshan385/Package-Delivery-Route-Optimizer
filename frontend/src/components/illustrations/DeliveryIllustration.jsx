import React from 'react';

export default function DeliveryIllustration({ className = '', style = {} }) {
  return (
    <svg
      viewBox="0 0 560 380"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      style={{ width: '100%', height: 'auto', display: 'block', ...style }}
      role="img"
      aria-label="Package delivery route illustration"
    >
      {/* Background Subtle Grid & Ground Plane */}
      <g opacity="0.4">
        {/* Map grid lines */}
        <line x1="40" y1="90" x2="520" y2="90" stroke="var(--color-border)" strokeWidth="1" strokeDasharray="4 4" />
        <line x1="40" y1="160" x2="520" y2="160" stroke="var(--color-border)" strokeWidth="1" strokeDasharray="4 4" />
        <line x1="40" y1="230" x2="520" y2="230" stroke="var(--color-border)" strokeWidth="1" strokeDasharray="4 4" />
        <line x1="120" y1="40" x2="120" y2="300" stroke="var(--color-border)" strokeWidth="1" strokeDasharray="4 4" />
        <line x1="280" y1="40" x2="280" y2="300" stroke="var(--color-border)" strokeWidth="1" strokeDasharray="4 4" />
        <line x1="440" y1="40" x2="440" y2="300" stroke="var(--color-border)" strokeWidth="1" strokeDasharray="4 4" />
      </g>

      {/* Main Ground Line */}
      <line x1="30" y1="310" x2="530" y2="310" stroke="var(--color-border)" strokeWidth="2" />
      {/* Subtle road marks */}
      <line x1="60" y1="310" x2="110" y2="310" stroke="var(--color-primary)" strokeWidth="2" />
      <line x1="180" y1="310" x2="230" y2="310" stroke="var(--color-primary)" strokeWidth="2" />
      <line x1="300" y1="310" x2="350" y2="310" stroke="var(--color-primary)" strokeWidth="2" />
      <line x1="420" y1="310" x2="470" y2="310" stroke="var(--color-primary)" strokeWidth="2" />

      {/* Dijkstra Shortest Path Curve */}
      <path
        d="M 80 170 C 140 100, 200 130, 270 90 C 340 50, 420 110, 480 80"
        stroke="var(--color-primary)"
        strokeWidth="2.5"
        strokeDasharray="6 5"
        strokeLinecap="round"
      />

      {/* Waypoint Stops along Dijkstra Path */}
      {/* Stop 1 (Origin Hub) */}
      <g>
        <circle cx="80" cy="170" r="16" fill="var(--color-surface)" stroke="var(--color-primary)" strokeWidth="2" />
        <circle cx="80" cy="170" r="6" fill="var(--color-primary)" />
        <rect x="55" y="196" width="50" height="18" rx="4" fill="var(--color-surface)" stroke="var(--color-border)" strokeWidth="1" />
        <text x="80" y="209" fill="var(--color-text)" fontSize="9" fontWeight="700" textAnchor="middle" fontFamily="sans-serif">
          DEPOT
        </text>
      </g>

      {/* Stop 2 (Intermediate Node) */}
      <g>
        <circle cx="270" cy="90" r="12" fill="var(--color-surface)" stroke="var(--color-accent)" strokeWidth="2" />
        <circle cx="270" cy="90" r="4" fill="var(--color-accent)" />
        <rect x="245" y="58" width="50" height="18" rx="4" fill="var(--color-surface)" stroke="var(--color-border)" strokeWidth="1" />
        <text x="270" y="70" fill="var(--color-muted)" fontSize="9" fontWeight="600" textAnchor="middle" fontFamily="sans-serif">
          STOP #1
        </text>
      </g>

      {/* Stop 3 (Destination Location Pin) */}
      <g>
        <circle cx="480" cy="80" r="18" fill="var(--color-surface)" stroke="var(--color-primary)" strokeWidth="2" />
        {/* Pin marker shape */}
        <path
          d="M 480 68 C 475 68, 471 72, 471 77 C 471 83, 480 92, 480 92 C 480 92, 489 83, 489 77 C 489 72, 485 68, 480 68 Z"
          fill="var(--color-primary)"
        />
        <circle cx="480" cy="76" r="2.5" fill="#FFFFFF" />
        <rect x="445" y="104" width="70" height="18" rx="4" fill="var(--color-surface)" stroke="var(--color-primary)" strokeWidth="1" />
        <text x="480" y="117" fill="var(--color-primary)" fontSize="9" fontWeight="700" textAnchor="middle" fontFamily="sans-serif">
          DESTINATION
        </text>
      </g>

      {/* DELIVERY VAN (Flat Minimalist Asset) */}
      <g transform="translate(180, 205)">
        {/* Motion lines behind van */}
        <line x1="-35" y1="50" x2="-10" y2="50" stroke="var(--color-muted)" strokeWidth="2" strokeLinecap="round" opacity="0.6" />
        <line x1="-45" y1="65" x2="-15" y2="65" stroke="var(--color-muted)" strokeWidth="2" strokeLinecap="round" opacity="0.6" />
        <line x1="-30" y1="80" x2="-5" y2="80" stroke="var(--color-muted)" strokeWidth="2" strokeLinecap="round" opacity="0.6" />

        {/* Shadow plane flat */}
        <ellipse cx="100" cy="104" rx="100" ry="6" fill="var(--color-border)" opacity="0.7" />

        {/* Van Main Cargo Body */}
        <rect x="0" y="20" width="135" height="75" rx="6" fill="var(--color-surface)" stroke="var(--color-border)" strokeWidth="2" />

        {/* Green Brand Accent Band */}
        <rect x="0" y="55" width="200" height="12" fill="var(--color-primary)" />

        {/* Cargo Door Seam */}
        <line x1="45" y1="20" x2="45" y2="95" stroke="var(--color-border)" strokeWidth="1.5" />
        {/* Door handle */}
        <rect x="40" y="50" width="3" height="10" rx="1" fill="var(--color-muted)" />

        {/* Van Front Cab */}
        <path
          d="M 135 38 L 165 38 L 195 65 L 200 95 L 135 95 Z"
          fill="var(--color-surface)"
          stroke="var(--color-border)"
          strokeWidth="2"
        />

        {/* Cab Windshield Glass */}
        <path
          d="M 142 44 L 163 44 L 186 64 L 142 64 Z"
          fill="var(--color-elevated)"
          stroke="var(--color-border)"
          strokeWidth="1.5"
        />

        {/* Headlight */}
        <rect x="195" y="72" width="5" height="10" rx="2" fill="#EAB308" />

        {/* Wheels */}
        {/* Rear Wheel */}
        <circle cx="35" cy="95" r="18" fill="var(--color-dark-bg, #1A1D1B)" stroke="var(--color-border)" strokeWidth="2" />
        <circle cx="35" cy="95" r="8" fill="var(--color-elevated)" />
        <circle cx="35" cy="95" r="3" fill="var(--color-primary)" />

        {/* Front Wheel */}
        <circle cx="165" cy="95" r="18" fill="var(--color-dark-bg, #1A1D1B)" stroke="var(--color-border)" strokeWidth="2" />
        <circle cx="165" cy="95" r="8" fill="var(--color-elevated)" />
        <circle cx="165" cy="95" r="3" fill="var(--color-primary)" />

        {/* Van side logo text */}
        <text x="18" y="44" fill="var(--color-primary)" fontSize="10" fontWeight="700" letterSpacing="0.5" fontFamily="sans-serif">
          EXPRESS
        </text>
      </g>

      {/* DELIVERY PACKAGE / PARCEL BOX (Foreground Left) */}
      <g transform="translate(60, 220)">
        {/* Parcel base */}
        <rect x="0" y="15" width="70" height="65" rx="4" fill="var(--color-elevated)" stroke="var(--color-border)" strokeWidth="1.5" />
        {/* Tape band */}
        <rect x="30" y="15" width="10" height="65" fill="var(--color-primary)" opacity="0.85" />
        <rect x="0" y="42" width="70" height="10" fill="var(--color-primary)" opacity="0.85" />
        {/* Shipping Label */}
        <rect x="6" y="22" width="20" height="15" rx="2" fill="var(--color-surface)" stroke="var(--color-border)" strokeWidth="1" />
        <line x1="9" y1="26" x2="23" y2="26" stroke="var(--color-muted)" strokeWidth="1" />
        <line x1="9" y1="30" x2="19" y2="30" stroke="var(--color-muted)" strokeWidth="1" />
        <line x1="9" y1="33" x2="21" y2="33" stroke="var(--color-muted)" strokeWidth="1" />
        {/* Barcode lines */}
        <line x1="44" y1="58" x2="44" y2="72" stroke="var(--color-text)" strokeWidth="1.5" />
        <line x1="47" y1="58" x2="47" y2="72" stroke="var(--color-text)" strokeWidth="1" />
        <line x1="50" y1="58" x2="50" y2="72" stroke="var(--color-text)" strokeWidth="2" />
        <line x1="54" y1="58" x2="54" y2="72" stroke="var(--color-text)" strokeWidth="1.5" />
        <line x1="58" y1="58" x2="58" y2="72" stroke="var(--color-text)" strokeWidth="1" />
        <line x1="61" y1="58" x2="61" y2="72" stroke="var(--color-text)" strokeWidth="2" />
      </g>

      {/* SECOND PARCEL (Stacked small) */}
      <g transform="translate(100, 245)">
        <rect x="0" y="0" width="45" height="40" rx="3" fill="var(--color-surface)" stroke="var(--color-border)" strokeWidth="1.5" />
        <rect x="18" y="0" width="8" height="40" fill="var(--color-accent)" opacity="0.8" />
        {/* Fragile arrow icon */}
        <path d="M 8 18 L 8 26 M 6 20 L 8 18 L 10 20" stroke="var(--color-muted)" strokeWidth="1" strokeLinecap="round" />
        <path d="M 13 18 L 13 26 M 11 20 L 13 18 L 15 20" stroke="var(--color-muted)" strokeWidth="1" strokeLinecap="round" />
      </g>

      {/* FLOATING ROUTE METRIC BADGE (Top Center) */}
      <g transform="translate(230, 20)">
        <rect x="0" y="0" width="140" height="26" rx="6" fill="var(--color-surface)" stroke="var(--color-border)" strokeWidth="1" />
        <circle cx="14" cy="13" r="4" fill="var(--color-primary)" />
        <text x="26" y="17" fill="var(--color-text)" fontSize="10" fontWeight="700" fontFamily="sans-serif">
          DIJKSTRA OPTIMAL
        </text>
      </g>
    </svg>
  );
}
