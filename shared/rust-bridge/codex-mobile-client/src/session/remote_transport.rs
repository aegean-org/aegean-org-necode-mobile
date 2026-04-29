//! Transport-agnostic reconnect surface for remote `AppServerClient`s.
//!
//! Both the SSH (russh + WebSocket-over-tunnel) and Alleycat (Iroh QUIC)
//! transports need to be able to re-establish their underlying connection
//! and produce a fresh `AppServerClient` after a transient drop. This module
//! defines the small trait the session worker uses to drive that operation
//! without knowing which transport is underneath.

use std::sync::Arc;

use async_trait::async_trait;
use codex_app_server_client::{AppServerClient, RemoteAppServerConnectArgs};

use crate::transport::TransportError;

/// Result of a successful reconnect.
pub(crate) struct Reconnected {
    /// Fresh client wired to the newly-established transport.
    pub client: AppServerClient,
    /// Transport-scoped state that must outlive the worker's `client` binding
    /// (e.g. the iroh `Endpoint` backing an Alleycat stream). The worker swaps
    /// this in on each successful reconnect so the previous keepalive is only
    /// dropped after the new one is installed.
    pub keepalive: Option<Arc<dyn Send + Sync>>,
}

/// Reconnect strategy for a remote `AppServerClient`.
///
/// Implementations are held by the session worker as `Arc<dyn RemoteTransport>`.
/// Reconnect runs only when the existing client drops (cold path), so dynamic
/// dispatch overhead is irrelevant compared to the network handshake cost.
#[async_trait]
pub(crate) trait RemoteTransport: Send + Sync + 'static {
    /// Re-establish the underlying transport and return a fresh client.
    ///
    /// `args` and `websocket_url` describe the original connect parameters and
    /// are provided for transports that fall back to a plain WebSocket connect
    /// (e.g. SSH after a port-forward refresh). Transports that ignore them
    /// (e.g. Alleycat, which derives everything from its own params) may do so.
    async fn reconnect(
        &self,
        args: &RemoteAppServerConnectArgs,
        websocket_url: &str,
    ) -> Result<Reconnected, TransportError>;
}

#[cfg(test)]
mod tests {
    //! Tests for the `RemoteTransport` contract surface.
    //!
    //! Exercising `reconnect_remote_client` end-to-end requires constructing a
    //! real `AppServerClient`, which has no public test constructor. So these
    //! tests cover the parts that are testable in isolation: drop ordering of
    //! the keepalive slot the worker maintains, and the trait being usable as
    //! `Arc<dyn RemoteTransport>`.

    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    struct DropCounter {
        counter: Arc<AtomicUsize>,
    }

    impl Drop for DropCounter {
        fn drop(&mut self) {
            self.counter.fetch_add(1, Ordering::SeqCst);
        }
    }

    /// The worker maintains `let mut keepalive: Option<Arc<dyn Send + Sync>>`
    /// and on a successful reconnect runs `*keepalive = next.keepalive`.
    /// Verify that assigning a new `Some(...)` drops the previous Arc, and
    /// that holding `None` leaves the previous Arc untouched.
    #[test]
    fn keepalive_assignment_drops_previous_arc() {
        let drop_count = Arc::new(AtomicUsize::new(0));
        let initial = Arc::new(DropCounter {
            counter: Arc::clone(&drop_count),
        });
        let mut keepalive: Option<Arc<dyn Send + Sync>> =
            Some(initial.clone() as Arc<dyn Send + Sync>);

        // Drop the local strong reference; only `keepalive` keeps it alive now.
        drop(initial);
        assert_eq!(drop_count.load(Ordering::SeqCst), 0);

        // SSH-style reconnect: keepalive is None, slot must NOT be cleared.
        let next_ssh: Option<Arc<dyn Send + Sync>> = None;
        if next_ssh.is_some() {
            keepalive = next_ssh;
        }
        assert_eq!(
            drop_count.load(Ordering::SeqCst),
            0,
            "an SSH reconnect (keepalive: None) must not clobber the existing keepalive"
        );

        // Alleycat-style reconnect: keepalive carries a fresh Arc; old one drops.
        let next_alleycat_drop_count = Arc::new(AtomicUsize::new(0));
        let next_alleycat = Arc::new(DropCounter {
            counter: Arc::clone(&next_alleycat_drop_count),
        });
        keepalive = Some(next_alleycat as Arc<dyn Send + Sync>);
        assert_eq!(
            drop_count.load(Ordering::SeqCst),
            1,
            "the previous keepalive Arc must drop exactly once after the swap"
        );
        assert_eq!(
            next_alleycat_drop_count.load(Ordering::SeqCst),
            0,
            "the new keepalive Arc must remain alive"
        );

        // Worker exit: dropping the slot drops the new keepalive.
        drop(keepalive);
        assert_eq!(next_alleycat_drop_count.load(Ordering::SeqCst), 1);
    }

    /// `RemoteTransport` must be usable as a trait object (`Arc<dyn ...>`).
    /// This compiles iff the trait stays object-safe.
    #[test]
    fn trait_is_object_safe() {
        struct FakeTransport;

        #[async_trait]
        impl RemoteTransport for FakeTransport {
            async fn reconnect(
                &self,
                _args: &RemoteAppServerConnectArgs,
                _websocket_url: &str,
            ) -> Result<Reconnected, TransportError> {
                Err(TransportError::ConnectionFailed("test".into()))
            }
        }

        let _t: Arc<dyn RemoteTransport> = Arc::new(FakeTransport);
    }
}
