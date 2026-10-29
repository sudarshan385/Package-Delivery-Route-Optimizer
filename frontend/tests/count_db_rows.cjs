const { Client } = require('pg');

async function countRows() {
  const client = new Client({
    connectionString: 'postgresql://app_user:app_password@localhost:5432/packagedeliveryrouteoptimizer'
  });

  try {
    await client.connect();
    console.log('Connected to packagedeliveryrouteoptimizer:');
    const tables = [
      'locations',
      'location_edges',
      'users',
      'drivers',
      'vehicles',
      'packages',
      'deliveries',
      'delivery_legs',
      'shipment_events',
      'flyway_schema_history'
    ];

    for (const t of tables) {
      const res = await client.query(`SELECT count(*) FROM ${t}`);
      console.log(`  ${t}: ${res.rows[0].count}`);
    }

    const flyway = await client.query('SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank');
    console.log('\nFlyway Migrations Status:');
    flyway.rows.forEach(r => {
      console.log(`  V${r.version}: ${r.description} (Success: ${r.success})`);
    });

  } catch (err) {
    console.error('Error counting DB rows:', err.message);
  } finally {
    await client.end();
  }
}

countRows();
