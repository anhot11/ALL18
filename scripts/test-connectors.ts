import { registry } from '../src/lib/connectors/registry';
import { getAllProviders } from '../src/lib/db';

async function main() {
  console.log('--- TESTING NEXUS18 / ALL18 BACKEND ---');

  // 1. Check DB providers
  const providers = getAllProviders();
  console.log(`[DB] Loaded ${providers.length} registered providers.`);
  providers.forEach((p) => {
    console.log(` - ${p.id} (${p.name}): ${p.category} [priority: ${p.priority}]`);
  });

  // 2. Test individual connectors
  const testIds = ['eporner', 'rule34', 'danbooru'];
  for (const id of testIds) {
    const connector = registry.getConnector(id);
    if (!connector) {
      console.error(`Missing connector: ${id}`);
      continue;
    }
    console.log(`\nTesting [${connector.name}] trending feed...`);
    try {
      const items = await connector.getTrending(1);
      console.log(`[${connector.name}] returned ${items.length} items.`);
      if (items.length > 0) {
        console.log(`Sample item: "${items[0].title}" | Thumb: ${items[0].thumbnail.slice(0, 60)}... | Duration: ${items[0].duration}s`);
      }
    } catch (e: any) {
      console.error(`[${connector.name}] error:`, e.message);
    }
  }

  // 3. Test Aggregated search
  console.log('\nTesting Aggregated multi-provider search ("blonde")...');
  const searchResults = await registry.searchAll('blonde', 1);
  console.log(`Aggregated search returned ${searchResults.length} merged items across providers!`);
}

main().catch(console.error);
