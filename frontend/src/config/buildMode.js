// Optional env is needed for plain Node test imports; Vite replaces the direct flag access.
export const LOCAL_TEST_MODE = typeof import.meta.env !== 'undefined' && import.meta.env.VITE_COOKX_LOCAL_TEST === 'true'
