import "@testing-library/jest-dom";

// Mock ResizeObserver which is used by react-virtuoso
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = ResizeObserverMock;

// Mock react-virtuoso to render all items synchronously in testing environment
jest.mock("react-virtuoso", () => {
  const React = require("react");
  return {
    Virtuoso: ({ data = [], itemContent, components, ...props }: any) => {
      const List =
        components?.List ||
        (({ children }: any) => React.createElement("div", null, children));
      const Header = components?.Header || (() => null);
      const Footer = components?.Footer || (() => null);
      return React.createElement(
        "div",
        { "data-testid": "virtuoso-mock" },
        React.createElement(Header, null),
        React.createElement(
          List,
          null,
          data.map((item: any, index: number) => itemContent(index, item))
        ),
        React.createElement(Footer, null)
      );
    },
  };
});
