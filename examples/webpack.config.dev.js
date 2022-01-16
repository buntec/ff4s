var webpack = require('webpack');

module.exports = require('./scalajs.webpack.config');

const Path = require('path');
const rootDir = Path.resolve(__dirname, '../../../..');
module.exports.infrastructureLogging = {
  colors: true,
  stream: process.stdout,
};

module.exports.plugins = [
  new webpack.EnvironmentPlugin({
    CATS_EFFECT_TRACING_MODE: 'none'
  })
]

module.exports.devServer = {
    static: [
      { directory: Path.resolve(__dirname, 'dev') },
      { directory: Path.resolve(rootDir, 'assets') }, 
    ],
    proxy: {
      '/api': 'http://localhost:8090',
    },
    hot: true,
    //hotOnly: false, // only reload when build is successful
    //inline: true // live reloading
};
