package httpdns

import "errors"

var (
	ErrInvalidConfig      = errors.New("invalid config")
	ErrInvalidDomain      = errors.New("invalid domain")
	ErrTooManyDomains     = errors.New("too many domains, max 5")
	ErrServiceUnavailable = errors.New("service unavailable")
)

type HTTPDNSError struct {
	Op     string
	Domain string
	Err    error
}

func (e *HTTPDNSError) Error() string {
	if e.Domain != "" {
		return e.Op + ": " + e.Domain + ": " + e.Err.Error()
	}
	return e.Op + ": " + e.Err.Error()
}

func (e *HTTPDNSError) Unwrap() error { return e.Err }

func NewHTTPDNSError(op, domain string, err error) error {
	if err == nil {
		return nil
	}
	return &HTTPDNSError{Op: op, Domain: domain, Err: err}
}
