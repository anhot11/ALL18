const { contextBridge } = require('electron');

contextBridge.exposeInMainWorld('nexus18Desktop', {
  platform: process.platform,
  isElectron: true,
  version: '1.0.0',
});
