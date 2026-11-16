module.exports = {
  '/api/**': {
    target: process.env.SCHEMABRIDGE_API_TARGET || 'http://localhost:8080',
    secure: false
  }
};