import React, { useMemo, useState } from 'react';
import { Layers, Zap, Info, Compass } from 'lucide-react';

/**
 * NetworkGraphVisualizer:
 * Renders an interactive, futuristic SVG directed graph of locations and road edges.
 * Supports highlighting Dijkstra path vertices and edges, node clicking to set origin/destination,
 * hover tooltips, and futuristic glowing animations.
 */
export default function NetworkGraphVisualizer({
  locations = [],
  edges = [],
  activePathNames = [],
  onSelectNode,
  sourceId,
  destId,
  height = '440px',
  className = '',
}) {
  const [hoveredNode, setHoveredNode] = useState(null);
  const [hoveredEdge, setHoveredEdge] = useState(null);

  // Filter valid locations with lat/lon
  const validLocations = useMemo(() => {
    return locations.filter(
      (l) => l && typeof l.latitude === 'number' && typeof l.longitude === 'number' && !isNaN(l.latitude) && !isNaN(l.longitude)
    );
  }, [locations]);

  // Compute bounding box and project geographic coordinates into SVG canvas (1000 x 600)
  const { projectedNodes, bounds } = useMemo(() => {
    const width = 960;
    const height = 540;
    const padding = 60;

    if (validLocations.length === 0) {
      return { projectedNodes: new Map(), bounds: null };
    }

    let minLat = Infinity,
      maxLat = -Infinity,
      minLon = Infinity,
      maxLon = -Infinity;

    validLocations.forEach((loc) => {
      if (loc.latitude < minLat) minLat = loc.latitude;
      if (loc.latitude > maxLat) maxLat = loc.latitude;
      if (loc.longitude < minLon) minLon = loc.longitude;
      if (loc.longitude > maxLon) maxLon = loc.longitude;
    });

    // Avoid division by zero if all points are identical
    const latSpan = maxLat - minLat || 0.05;
    const lonSpan = maxLon - minLon || 0.05;

    const projected = new Map();
    validLocations.forEach((loc) => {
      // Mercator-like inverted Y for SVG
      const x = padding + ((loc.longitude - minLon) / lonSpan) * (width - 2 * padding);
      const y = height - padding - ((loc.latitude - minLat) / latSpan) * (height - 2 * padding);
      projected.set(loc.id, {
        ...loc,
        x,
        y,
      });
    });

    return {
      projectedNodes: projected,
      bounds: { minLat, maxLat, minLon, maxLon },
    };
  }, [validLocations]);

  // Create a fast lookup for path vertices and path edges
  const activePathSet = useMemo(() => new Set(activePathNames || []), [activePathNames]);

  const activeEdgePairs = useMemo(() => {
    const pairs = new Set();
    if (!activePathNames || activePathNames.length < 2) return pairs;
    for (let i = 0; i < activePathNames.length - 1; i++) {
      pairs.add(`${activePathNames[i]}->${activePathNames[i + 1]}`);
    }
    return pairs;
  }, [activePathNames]);

  return (
    <div
      className={`relative w-full overflow-hidden rounded-xl bg-[#090d16] border border-cyan-900/40 shadow-2xl ${className}`}
      style={{ height }}
    >
      {/* Background Futuristic Grid & Radial Glow */}
      <div
        className="absolute inset-0 pointer-events-none opacity-20"
        style={{
          backgroundImage: `
            linear-gradient(to right, rgba(6, 182, 212, 0.1) 1px, transparent 1px),
            linear-gradient(to bottom, rgba(6, 182, 212, 0.1) 1px, transparent 1px)
          `,
          backgroundSize: '36px 36px',
        }}
      />
      <div className="absolute top-0 right-0 w-96 h-96 bg-cyan-500/5 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-0 left-0 w-96 h-96 bg-emerald-500/5 rounded-full blur-3xl pointer-events-none" />

      {/* Top Overlay Badge */}
      <div className="absolute top-3 left-4 z-10 flex items-center gap-2 bg-[#0d1526]/80 backdrop-blur-md px-3 py-1.5 rounded-lg border border-cyan-500/30 text-xs">
        <Zap size={14} className="text-cyan-400 animate-pulse" />
        <span className="font-mono text-cyan-300 font-semibold tracking-wide">
          BENGALURU TOPOLOGY GRAPH
        </span>
        <span className="text-slate-400 font-mono text-[11px] ml-1">
          • {validLocations.length} Vertices • {edges.length} Directed Edges
        </span>
      </div>

      {/* SVG Canvas */}
      <svg
        viewBox="0 0 960 540"
        className="w-full h-full select-none"
        preserveAspectRatio="xMidYMid meet"
      >
        <defs>
          {/* Arrow markers for edges */}
          <marker
            id="edge-arrow-default"
            viewBox="0 0 10 10"
            refX="22"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M 0 1 L 9 5 L 0 9 z" fill="#334155" />
          </marker>
          <marker
            id="edge-arrow-active"
            viewBox="0 0 10 10"
            refX="24"
            refY="5"
            markerWidth="7"
            markerHeight="7"
            orient="auto-start-reverse"
          >
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#06b6d4" />
          </marker>

          {/* Node Glow Filters */}
          <filter id="glow-cyan" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur in="SourceGraphic" stdDeviation="5" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <filter id="glow-mint" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur in="SourceGraphic" stdDeviation="6" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* 1. Render Directed Edges */}
        <g className="edges-layer">
          {edges.map((edge) => {
            const src = projectedNodes.get(edge.sourceLocationId);
            const dst = projectedNodes.get(edge.destinationLocationId);
            if (!src || !dst) return null;

            const isPathEdge =
              activeEdgePairs.has(`${src.name}->${dst.name}`) ||
              (edge.sourceLocationName &&
                edge.destinationLocationName &&
                activeEdgePairs.has(`${edge.sourceLocationName}->${edge.destinationLocationName}`));

            const strokeColor = isPathEdge ? '#06b6d4' : '#1e293b';
            const strokeWidth = isPathEdge ? 3.5 : 1.2;

            return (
              <g key={`edge-${edge.id || `${edge.sourceLocationId}-${edge.destinationLocationId}`}`}>
                <line
                  x1={src.x}
                  y1={src.y}
                  x2={dst.x}
                  y2={dst.y}
                  stroke={strokeColor}
                  strokeWidth={strokeWidth}
                  strokeDasharray={isPathEdge ? '8 4' : 'none'}
                  markerEnd={isPathEdge ? 'url(#edge-arrow-active)' : 'url(#edge-arrow-default)'}
                  className={isPathEdge ? 'animate-[dash_1.5s_linear_infinite]' : 'opacity-60'}
                  onMouseEnter={() => setHoveredEdge(edge)}
                  onMouseLeave={() => setHoveredEdge(null)}
                />
                {/* Secondary animated glowing pulse for active path */}
                {isPathEdge && (
                  <line
                    x1={src.x}
                    y1={src.y}
                    x2={dst.x}
                    y2={dst.y}
                    stroke="#10b981"
                    strokeWidth="1.5"
                    strokeDasharray="4 12"
                    className="opacity-75"
                  />
                )}
              </g>
            );
          })}
        </g>

        {/* 2. Render Nodes */}
        <g className="nodes-layer">
          {Array.from(projectedNodes.values()).map((node) => {
            const isSource = String(node.id) === String(sourceId);
            const isDest = String(node.id) === String(destId);
            const isPathNode = activePathSet.has(node.name);
            const isHub =
              node.locationType?.toUpperCase() === 'HUB' ||
              node.locationType?.toUpperCase() === 'DEPOT';

            let fillColor = '#1e293b';
            let strokeColor = '#475569';
            let radius = isHub ? 9 : 7;
            let filter = 'none';

            if (isSource) {
              fillColor = '#06b6d4';
              strokeColor = '#ffffff';
              radius = 12;
              filter = 'url(#glow-cyan)';
            } else if (isDest) {
              fillColor = '#10b981';
              strokeColor = '#ffffff';
              radius = 12;
              filter = 'url(#glow-mint)';
            } else if (isPathNode) {
              fillColor = '#0ea5e9';
              strokeColor = '#38bdf8';
              radius = 10;
              filter = 'url(#glow-cyan)';
            } else if (isHub) {
              fillColor = '#6366f1';
              strokeColor = '#818cf8';
              radius = 9;
            }

            return (
              <g
                key={`node-${node.id}`}
                transform={`translate(${node.x}, ${node.y})`}
                className="cursor-pointer transition-transform duration-200 hover:scale-125"
                onClick={() => onSelectNode && onSelectNode(node)}
                onMouseEnter={() => setHoveredNode(node)}
                onMouseLeave={() => setHoveredNode(null)}
              >
                {/* Outer halo ripple for active nodes */}
                {(isSource || isDest || isPathNode) && (
                  <circle
                    r={radius + 8}
                    fill="none"
                    stroke={isSource ? '#06b6d4' : isDest ? '#10b981' : '#38bdf8'}
                    strokeWidth="1.5"
                    strokeOpacity="0.4"
                    className="animate-ping"
                  />
                )}

                <circle
                  r={radius}
                  fill={fillColor}
                  stroke={strokeColor}
                  strokeWidth={isSource || isDest ? 2.5 : 1.5}
                  filter={filter}
                />

                {/* Node Label */}
                <text
                  x={0}
                  y={radius + 13}
                  textAnchor="middle"
                  className="text-[10px] font-medium fill-slate-300 font-mono tracking-tight pointer-events-none drop-shadow-md select-none"
                  style={{
                    fill: isSource
                      ? '#38bdf8'
                      : isDest
                      ? '#34d399'
                      : isPathNode
                      ? '#7dd3fc'
                      : '#94a3b8',
                    fontWeight: isSource || isDest || isPathNode ? 700 : 500,
                  }}
                >
                  {node.name.length > 18 ? `${node.name.substring(0, 16)}…` : node.name}
                </text>
              </g>
            );
          })}
        </g>
      </svg>

      {/* Floating Hover Tooltip */}
      {hoveredNode && (
        <div
          className="absolute z-20 pointer-events-none bg-[#090d16]/95 backdrop-blur-md border border-cyan-500/40 rounded-lg p-3 shadow-2xl text-xs text-white max-w-[260px]"
          style={{
            bottom: '16px',
            right: '16px',
          }}
        >
          <div className="flex items-center justify-between gap-2 border-b border-slate-700/60 pb-1 mb-1.5">
            <span className="font-bold text-cyan-300 truncate">{hoveredNode.name}</span>
            <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-cyan-950 text-cyan-400 border border-cyan-800">
              {hoveredNode.locationType || 'NODE'}
            </span>
          </div>
          <div className="space-y-1 text-slate-300 font-mono text-[11px]">
            <div>Lat: {hoveredNode.latitude.toFixed(5)}</div>
            <div>Lon: {hoveredNode.longitude.toFixed(5)}</div>
            {hoveredNode.address && (
              <div className="text-slate-400 text-[10px] line-clamp-2 mt-1">
                {hoveredNode.address}
              </div>
            )}
          </div>
          <div className="mt-2 text-[10px] text-cyan-400/90 italic">
            Click to set as Source or Destination
          </div>
        </div>
      )}

      {/* Graph Legend */}
      <div className="absolute bottom-3 left-4 z-10 flex items-center gap-3 bg-[#0d1526]/80 backdrop-blur-md px-3 py-1.5 rounded-lg border border-slate-800 text-[11px] text-slate-300">
        <div className="flex items-center gap-1.5">
          <div className="w-2.5 h-2.5 rounded-full bg-cyan-400 shadow-[0_0_8px_#06b6d4]" />
          <span>Source</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 shadow-[0_0_8px_#10b981]" />
          <span>Destination</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-2.5 h-2.5 rounded-full bg-indigo-500" />
          <span>Central Hub</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-4 h-0.5 bg-cyan-400" />
          <span>Dijkstra Path</span>
        </div>
      </div>
    </div>
  );
}
