-- Add geometry_geojson to location_edges and route_edges
ALTER TABLE location_edges ADD COLUMN IF NOT EXISTS geometry_geojson TEXT;
ALTER TABLE route_edges ADD COLUMN IF NOT EXISTS geometry_geojson TEXT;
